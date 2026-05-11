-- Fix "column reference display_name is ambiguous" in insane.redeem_friend_code.
--
-- The OUT parameter `display_name` collides with insane.profiles.display_name
-- inside the SELECT … INTO target. Qualify the column with a table alias so
-- Postgres resolves it to the profile row.

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

  select p.id, p.display_name, p.code_expires_at
    into target
    from insane.profiles p
   where upper(p.friend_code) = upper(p_code)
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
