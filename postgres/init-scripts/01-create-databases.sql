-- Initialize multiple databases for Hephaestus
-- This script creates separate databases for the application server and intelligence service
-- while using the same PostgreSQL instance and user credentials

-- Create the mentor database for the intelligence service (chainlit)
-- The hephaestus database is already created via POSTGRES_DB environment variable
CREATE DATABASE mentor;

-- Grant all privileges to the root user on both databases
GRANT ALL PRIVILEGES ON DATABASE hephaestus TO root;
GRANT ALL PRIVILEGES ON DATABASE mentor TO root;

-- Connect to the mentor database and ensure proper schema setup
\c mentor;

-- Create schema if needed (chainlit will handle table creation)
-- Grant necessary permissions for the user
GRANT ALL ON SCHEMA public TO root;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO root;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO root;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO root;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO root;

-- Switch back to hephaestus database
\c hephaestus;

-- Ensure proper permissions on the main database as well
GRANT ALL ON SCHEMA public TO root;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO root;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO root;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO root;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO root;
