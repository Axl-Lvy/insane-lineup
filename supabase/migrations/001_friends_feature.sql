-- Inase friends feature — full schema migration.
--
-- Paste this whole file into the Supabase SQL editor. It is idempotent (safe
-- to re-run): every CREATE uses IF NOT EXISTS / OR REPLACE.
--
-- Pre-requisites:
--   1. Enable Anonymous Sign-Ins under
--        Project → Authentication → Providers → "Anonymous Sign-Ins" → Enable.
--   2. Expose the `insane` schema via PostgREST under
--        Project Settings → API → Exposed schemas → add "insane".
--      Without that, /rest/v1 with `Accept-Profile: insane` returns 404.
--
-- All insane-related tables (lineup + friends-feature) live in a dedicated
-- `insane` schema. After running this, the sibling Axl-Lvy admin route that
-- writes to `insane_lineup` must target the `insane` schema (e.g.
-- `createClient(url, serviceRole, { db: { schema: 'insane' } })`) — it will
-- 404 against `public.insane_lineup` once the table moves.
--
-- Layout (the order matters because policies reference other tables):
--   §0 schema + grants
--   §1 move insane_lineup into insane
--   §2 create all tables (no policies yet — policies on one table can
--      reference another, so every table must exist first)
--   §3 enable RLS + create policies
--   §4 trigger that backfills profile rows from auth.users
--   §5 friend-code RPCs (security definer)

-- ─────────────────────────────────────────────────────────────────────────────
-- §0. Schema + role grants
-- ─────────────────────────────────────────────────────────────────────────────
create schema if not exists insane;

-- PostgREST routes by role; both anon and authenticated need USAGE on the
-- schema before they can touch anything inside it.
grant usage on schema insane to anon, authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- §1. Move the lineup table into the insane schema (idempotent).
--     `alter table if exists` makes this a no-op once the table has moved,
--     so the migration stays re-runnable. Existing policies, indexes, and
--     grants follow the table to its new schema.
-- ─────────────────────────────────────────────────────────────────────────────
alter table if exists public.insane_lineup set schema insane;

-- ─────────────────────────────────────────────────────────────────────────────
-- §2. Tables — create the bare structures first so policies in §3 can
--     reference each other freely.
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists insane.profiles (
  id              uuid primary key references auth.users(id) on delete cascade,
  display_name    text,
  friend_code     text unique,
  code_expires_at timestamptz,
  created_at      timestamptz not null default now()
);

create index if not exists profiles_friend_code_idx on insane.profiles(friend_code);

create table if not exists insane.favorites (
  user_id    uuid not null references auth.users(id) on delete cascade,
  fav_key    text not null,                -- "${day}|${stage}|${start}|${artist}"
  created_at timestamptz not null default now(),
  primary key (user_id, fav_key)
);

create index if not exists favorites_user_idx on insane.favorites(user_id);

-- Both columns FK to insane.profiles (not auth.users) so PostgREST can embed
-- display_name in a single query: `select=b_id,profile:profiles!friendships_b_id_fkey(display_name)`.
-- The cascade still reaches auth.users via profiles -> auth.users.
create table if not exists insane.friendships (
  a_id       uuid not null references insane.profiles(id) on delete cascade,
  b_id       uuid not null references insane.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (a_id, b_id),
  check (a_id <> b_id)
);

create index if not exists friendships_a_idx on insane.friendships(a_id);
create index if not exists friendships_b_idx on insane.friendships(b_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- §3. RLS + policies + table grants
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Lineup ──────────────────────────────────────────────────────────────────
-- Anonymous Supabase users carry the `authenticated` role with an
-- is_anonymous claim, NOT the `anon` role. The original "anon read lineup"
-- policy (still on the table from before the schema move) stops applying as
-- soon as the app signs in. Add an `authenticated` policy in parallel.
drop policy if exists "authenticated read lineup" on insane.insane_lineup;
create policy "authenticated read lineup"
  on insane.insane_lineup
  for select
  to authenticated
  using (true);

-- ── Profiles ────────────────────────────────────────────────────────────────
alter table insane.profiles enable row level security;

drop policy if exists "profiles read self or friend" on insane.profiles;
create policy "profiles read self or friend"
  on insane.profiles
  for select
  to authenticated
  using (
    id = auth.uid()
    or exists (
      select 1 from insane.friendships f
      where f.a_id = auth.uid() and f.b_id = profiles.id
    )
  );

drop policy if exists "profiles update self" on insane.profiles;
create policy "profiles update self"
  on insane.profiles
  for update
  to authenticated
  using (id = auth.uid())
  with check (id = auth.uid());

-- No INSERT policy — rows come from the §4 trigger.

-- PostgREST needs row-level grants too — RLS is the second filter, not the first.
grant select, update on insane.profiles to authenticated;

-- ── Favorites ───────────────────────────────────────────────────────────────
alter table insane.favorites enable row level security;

drop policy if exists "favorites read self or friend" on insane.favorites;
create policy "favorites read self or friend"
  on insane.favorites
  for select
  to authenticated
  using (
    user_id = auth.uid()
    or exists (
      select 1 from insane.friendships f
      where f.a_id = auth.uid() and f.b_id = favorites.user_id
    )
  );

drop policy if exists "favorites insert self" on insane.favorites;
create policy "favorites insert self"
  on insane.favorites
  for insert
  to authenticated
  with check (user_id = auth.uid());

drop policy if exists "favorites delete self" on insane.favorites;
create policy "favorites delete self"
  on insane.favorites
  for delete
  to authenticated
  using (user_id = auth.uid());

grant select, insert, delete on insane.favorites to authenticated;

-- ── Friendships ─────────────────────────────────────────────────────────────
alter table insane.friendships enable row level security;

drop policy if exists "friendships read mine" on insane.friendships;
create policy "friendships read mine"
  on insane.friendships
  for select
  to authenticated
  using (auth.uid() in (a_id, b_id));

drop policy if exists "friendships delete mine" on insane.friendships;
create policy "friendships delete mine"
  on insane.friendships
  for delete
  to authenticated
  using (auth.uid() in (a_id, b_id));

-- No INSERT policy — rows are inserted only via the §5 redeem RPC.
grant select, delete on insane.friendships to authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- §4. Trigger — auto-create a profile row whenever a new auth.users row
--     appears. search_path is pinned so the SECURITY DEFINER body never
--     resolves a name through the caller's path.
-- ─────────────────────────────────────────────────────────────────────────────
create or replace function insane.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = insane, public
as $$
begin
  insert into insane.profiles(id) values (new.id) on conflict do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute procedure insane.handle_new_user();

-- ─────────────────────────────────────────────────────────────────────────────
-- §5. RPCs — both run as SECURITY DEFINER so the writes bypass RLS
--     after we've checked the rules ourselves.
-- ─────────────────────────────────────────────────────────────────────────────

-- 6-char base32 (Crockford-ish, no I/L/O/U) code generator.
create or replace function insane.generate_friend_code()
returns text
language plpgsql
volatile
as $$
declare
  alphabet text := 'ABCDEFGHJKMNPQRSTVWXYZ23456789';
  code text := '';
  i int;
begin
  for i in 1..6 loop
    code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
  end loop;
  return code;
end;
$$;

-- Rotate the calling user's friend code (10-min TTL). Returns the new code.
create or replace function insane.rotate_friend_code()
returns table(code text, expires_at timestamptz)
language plpgsql
security definer
set search_path = insane, public
as $$
declare
  new_code text;
  expiry   timestamptz;
  attempts int := 0;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;

  -- Retry on the rare unique-collision until we get a free code.
  loop
    new_code := insane.generate_friend_code();
    expiry   := now() + interval '10 minutes';
    begin
      update insane.profiles
         set friend_code = new_code,
             code_expires_at = expiry
       where id = auth.uid();
      exit;
    exception when unique_violation then
      attempts := attempts + 1;
      if attempts > 5 then
        raise exception 'could not allocate a unique code';
      end if;
    end;
  end loop;

  return query select new_code, expiry;
end;
$$;

revoke all on function insane.rotate_friend_code() from public;
grant execute on function insane.rotate_friend_code() to authenticated;

-- Redeem a friend code: validates, inserts the friendship pair, returns the
-- friend's id + display_name. Clears the code after a successful redeem.
create or replace function insane.redeem_friend_code(p_code text)
returns table(friend_id uuid, display_name text)
language plpgsql
security definer
set search_path = insane, public
as $$
declare
  target record;
  me uuid := auth.uid();
begin
  if me is null then
    raise exception 'not authenticated';
  end if;
  if p_code is null or length(p_code) <> 6 then
    raise exception 'invalid code';
  end if;

  select id, display_name, code_expires_at
    into target
    from insane.profiles
   where upper(friend_code) = upper(p_code)
   limit 1;

  if not found then
    raise exception 'unknown code';
  end if;
  if target.id = me then
    raise exception 'cannot add yourself';
  end if;
  if target.code_expires_at is null or target.code_expires_at < now() then
    raise exception 'code expired';
  end if;

  insert into insane.friendships(a_id, b_id) values (me, target.id) on conflict do nothing;
  insert into insane.friendships(a_id, b_id) values (target.id, me) on conflict do nothing;

  -- Burn the code so the same QR can't be reused.
  update insane.profiles
     set friend_code = null,
         code_expires_at = null
   where id = target.id;

  friend_id := target.id;
  display_name := target.display_name;
  return next;
end;
$$;

revoke all on function insane.redeem_friend_code(text) from public;
grant execute on function insane.redeem_friend_code(text) to authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- §6. Aggregate favorite counts — RLS hides individual rows from non-friends,
--     so a direct SELECT can't answer "how many users favorited this set?".
--     SECURITY DEFINER lets the function aggregate across every row while
--     exposing only (fav_key, count) — no user_ids leak.
-- ─────────────────────────────────────────────────────────────────────────────
create or replace function insane.favorite_counts()
returns table(fav_key text, count bigint)
language sql
security definer
set search_path = insane, public
as $$
  select fav_key, count(*)::bigint
    from insane.favorites
   group by fav_key;
$$;

revoke all on function insane.favorite_counts() from public;
grant execute on function insane.favorite_counts() to authenticated;
