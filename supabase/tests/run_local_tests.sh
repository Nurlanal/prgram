#!/usr/bin/env bash
# Applies all migrations to a throw-away PostgreSQL database and runs the RLS / integrity tests.
# Usage: PGHOST=/var/tmp PGPORT=54329 PGUSER=postgres supabase/tests/run_local_tests.sh
set -euo pipefail
cd "$(dirname "$0")/.."
DB="${TEST_DB:-market_test}"
PSQL=(psql -X -q -v ON_ERROR_STOP=1)

"${PSQL[@]}" -d postgres -c "drop database if exists $DB" -c "create database $DB"
"${PSQL[@]}" -d "$DB" -f tests/00_supabase_shim.sql
for f in migrations/*.sql; do
  echo "→ migration $(basename "$f")"
  "${PSQL[@]}" -d "$DB" -f "$f"
done
echo "→ running tests/rls_and_integrity_tests.sql"
"${PSQL[@]}" -d "$DB" -o /dev/null -f tests/rls_and_integrity_tests.sql 2>&1 | sed "s/^psql:[^:]*:[0-9]*: //"

# Contract fixtures for the Android DTO tests (real rows as PostgREST would serialise them).
FIX=../app/src/test/resources/fixtures
mkdir -p "$FIX"
"${PSQL[@]}" -d "$DB" -tA -c "select row_to_json(s) from public.app_settings s" > "$FIX/app_settings.json"
"${PSQL[@]}" -d "$DB" -tA -c "select coalesce(json_agg(c order by sort_order), '[]') from public.categories c" > "$FIX/categories.json"
echo "ALL DATABASE TESTS PASSED"
