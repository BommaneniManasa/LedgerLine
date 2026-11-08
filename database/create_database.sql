-- Run once as a PostgreSQL superuser (e.g.  psql -U postgres -f database/create_database.sql ).
-- Replace the password placeholder BEFORE running. Flyway (inside the app) creates all tables on first start.
CREATE ROLE ledgerline_app LOGIN PASSWORD '<choose-a-strong-password>';
CREATE DATABASE ledgerline OWNER ledgerline_app;
