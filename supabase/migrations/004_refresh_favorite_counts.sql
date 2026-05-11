-- Re-register insane.favorite_counts and reload the PostgREST schema cache.
--
-- Symptom: `POST /rest/v1/rpc/favorite_counts` returns
--   PGRST202 "Could not find the function insane.favorite_counts in the schema cache"
-- despite the function being declared in 001. The other RPCs in the same
-- migration resolve fine, so the function existed at one point but the
-- PostgREST cache lost it (likely a partial deploy or a missed reload).
--
-- `create or replace` is safe to re-run; the trailing NOTIFY forces PostgREST
-- to rebuild its schema cache immediately rather than wait for the next DDL.

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

notify pgrst, 'reload schema';
