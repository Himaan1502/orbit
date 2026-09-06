-- ORBIT Database Initialization
-- This script runs on first container creation

-- Create orbit schema
CREATE SCHEMA IF NOT EXISTS orbit;

-- Grant permissions
GRANT ALL ON SCHEMA orbit TO orbit_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA orbit GRANT ALL ON TABLES TO orbit_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA orbit GRANT ALL ON SEQUENCES TO orbit_user;

-- Set default search path
ALTER USER orbit_user SET search_path TO orbit, public;
