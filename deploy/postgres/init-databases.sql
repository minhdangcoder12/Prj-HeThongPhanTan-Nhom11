-- One Postgres container, one logical database per service (database-per-service).
-- rideflow_user is created by POSTGRES_DB.
CREATE DATABASE rideflow_driver;
CREATE DATABASE rideflow_trip;
CREATE DATABASE rideflow_matching;
CREATE DATABASE rideflow_payment;
CREATE DATABASE rideflow_notification;
