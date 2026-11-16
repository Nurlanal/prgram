#!/usr/bin/env bash
# Concatenates all migrations into a single file that can be pasted into the Supabase SQL Editor.
set -euo pipefail
cd "$(dirname "$0")"
{
  echo "-- ============================================================================"
  echo "-- Supermarket platform — full database schema (generated, do not edit by hand)"
  echo "-- Source: supabase/migrations/*.sql — regenerate with supabase/build_schema.sh"
  echo "-- ============================================================================"
  for f in migrations/*.sql; do
    echo; echo "-- >>> $(basename "$f")"; cat "$f"
  done
} > schema_full.sql
echo "wrote supabase/schema_full.sql"
