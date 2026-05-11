-- Insane admin flag — adds is_admin to insane.profiles and lineup write policy.
--
-- Idempotent: safe to re-run.
--
-- Layout:
--   §1 add is_admin column on insane.profiles
--   §2 RLS — admins can update insane.insane_lineup
--   §3 RLS — anyone authenticated can read profiles.is_admin (so the app can
--          detect admin mode without exposing other profiles' fields).

-- ─────────────────────────────────────────────────────────────────────────────
-- §1. Column
-- ─────────────────────────────────────────────────────────────────────────────
alter table insane.profiles
  add column if not exists is_admin boolean not null default false;

-- ─────────────────────────────────────────────────────────────────────────────
-- §2. Lineup write policy — only admins.
--     SELECT policy stays as-is (public read via the existing
--     "authenticated read lineup" policy).
-- ─────────────────────────────────────────────────────────────────────────────
drop policy if exists "admin write lineup" on insane.insane_lineup;
create policy "admin write lineup"
  on insane.insane_lineup
  for all
  to authenticated
  using (
    exists (
      select 1 from insane.profiles p
      where p.id = auth.uid() and p.is_admin = true
    )
  )
  with check (
    exists (
      select 1 from insane.profiles p
      where p.id = auth.uid() and p.is_admin = true
    )
  );

-- The lineup table is in the `insane` schema; authenticated needs INSERT/UPDATE
-- on it to even reach the policy check.
grant insert, update, delete on insane.insane_lineup to authenticated;

-- ─────────────────────────────────────────────────────────────────────────────
-- §3. (Optional) Allow callers to read their own is_admin via the existing
--     "profiles read self or friend" policy — no change needed, it already
--     returns the full row for `id = auth.uid()`.
-- ─────────────────────────────────────────────────────────────────────────────
