-- Enable the extensions the schema depends on:
--   pgcrypto -> gen_random_uuid() for primary keys
--   vector   -> pgvector's VECTOR column type and similarity operators/indexes
--   citext   -> case-insensitive TEXT type, used for email columns
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;
