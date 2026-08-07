#!/bin/sh
# Startup wrapper for MongoDB replica set with authentication (keyFile).
#
# Why this exists:
#   The official mongo image fails with:
#     "security.keyFile is required when authorization is enabled with replica sets"
#   whenever --replSet is combined with MONGO_INITDB_ROOT_USERNAME/PASSWORD.
#
# How it works:
#   This script is used as the container ENTRYPOINT (running as root). It
#   generates the keyFile from the MONGODB_REPLICA_SET_KEY environment variable
#   into /etc (the container's writable layer), then hands control back to the
#   official docker-entrypoint.sh, which:
#     1. Performs the normal first-time bootstrap (creates the root user).
#     2. Starts mongod with --keyFile.
#
# Why /etc and not /data/db:
#   - Writing into /data/db would break docker-entrypoint.sh's empty-volume
#     detection and skip root-user creation.
#   - Writing into /etc avoids all Windows/macOS bind-mount permission issues
#     (the keyFile lives in the container layer, not on the host filesystem).

set -e

MONGODB_REPLICA_SET_KEY="${MONGODB_REPLICA_SET_KEY:-}"

if [ -z "$MONGODB_REPLICA_SET_KEY" ]; then
  echo "ERROR: MONGODB_REPLICA_SET_KEY environment variable is required when running" >&2
  echo "       a replica set with authorization enabled. Add it to your .env file." >&2
  exit 1
fi

KEYFILE=/etc/mongo-keyfile

# (Re)generate the keyFile on every start so it always matches the .env value.
printf '%s\n' "$MONGODB_REPLICA_SET_KEY" > "$KEYFILE"
chmod 400 "$KEYFILE"
chown mongodb:mongodb "$KEYFILE"

# Hand off to the official entrypoint, which performs the first-time
# initialisation (creating the root user) and then starts mongod with our args
# (the compose `command` already includes `mongod`, we just append --keyFile).
exec docker-entrypoint.sh "$@" --keyFile "$KEYFILE"
