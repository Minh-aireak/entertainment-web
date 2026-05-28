-- Debezium user
CREATE USER IF NOT EXISTS 'debezium'@'%'
IDENTIFIED BY 'REDACTED_DBZ_CREDENTIAL';

-- CDC permissions
GRANT SELECT, RELOAD, SHOW DATABASES,
       REPLICATION SLAVE,
       REPLICATION CLIENT,
       SHOW VIEW,
       TRIGGER
ON *.* TO 'debezium'@'%';

-- Schema permissions
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER
ON `identity-service`.* TO 'debezium'@'%';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER
ON `friend-service`.* TO 'debezium'@'%';

-- Root user
ALTER USER 'root'@'%'
IDENTIFIED BY 'REDACTED_LEGACY_CREDENTIAL';