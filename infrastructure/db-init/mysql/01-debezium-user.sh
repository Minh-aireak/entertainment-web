#!/bin/sh
set -eu

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${DEBEZIUM_DB_USERNAME:?DEBEZIUM_DB_USERNAME is required}"
: "${DEBEZIUM_DB_PASSWORD:?DEBEZIUM_DB_PASSWORD is required}"

escape_sql() {
  printf '%s' "$1" | sed "s/'/''/g"
}

DEBEZIUM_DB_USERNAME_ESCAPED="$(escape_sql "$DEBEZIUM_DB_USERNAME")"
DEBEZIUM_DB_PASSWORD_ESCAPED="$(escape_sql "$DEBEZIUM_DB_PASSWORD")"

# mysql_native_password (not the MySQL 8 default caching_sha2_password): the
# Debezium Kafka Connect JDBC driver refuses RSA public-key retrieval over a
# non-SSL connection, so a cold auth cache makes caching_sha2_password fail
# with a misleading "Access denied" even with the correct password.
MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<EOSQL
CREATE USER IF NOT EXISTS '${DEBEZIUM_DB_USERNAME_ESCAPED}'@'%'
IDENTIFIED WITH mysql_native_password BY '${DEBEZIUM_DB_PASSWORD_ESCAPED}';
ALTER USER '${DEBEZIUM_DB_USERNAME_ESCAPED}'@'%'
IDENTIFIED WITH mysql_native_password BY '${DEBEZIUM_DB_PASSWORD_ESCAPED}';
REVOKE ALL PRIVILEGES, GRANT OPTION
FROM '${DEBEZIUM_DB_USERNAME_ESCAPED}'@'%';

GRANT SELECT, RELOAD, SHOW DATABASES,
      REPLICATION SLAVE,
      REPLICATION CLIENT,
      LOCK TABLES
ON *.* TO '${DEBEZIUM_DB_USERNAME_ESCAPED}'@'%';

FLUSH PRIVILEGES;
EOSQL
