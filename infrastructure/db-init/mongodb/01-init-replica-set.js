// Initialize Replica Set for MongoDB (Required for Debezium CDC)
// This script runs from the mongo-init service AFTER mongodb is healthy.
// It connects with the root user via mongosh and configures:
//   1. Replica set "rs0" (single node)
//   2. Debezium user with CDC permissions

function initReplicaSet() {
  try {
    var status = rs.status();
    print("Replica set already initiated. ok=" + status.ok);
    return;
  } catch (e) {
    print("Initiating replica set rs0...");
    rs.initiate({
      _id: "rs0",
      members: [{ _id: 0, host: "mongodb:27017" }]
    });
    print("Replica set initiated.");
  }
}

initReplicaSet();

// Wait for replica set to become primary/healthy before creating users
print("Waiting for replica set to become ready...");
var isMaster = db.isMaster();
for (var i = 0; i < 30; i++) {
  if (isMaster.ismaster) {
    print("Replica set primary is ready.");
    break;
  }
  sleep(2000);
  isMaster = db.isMaster();
}

if (!isMaster.ismaster) {
  print("ERROR: Replica set did not become ready in time. Giving up.");
  quit(1);
}

// Create Debezium User with necessary roles for CDC (idempotent)
print("Checking/Creating debezium user...");
var existing = db.getSiblingDB('admin').getUser('debezium');
if (existing) {
  print("Debezium user already exists, skipping creation.");
} else {
  db.getSiblingDB('admin').createUser({
    user: 'debezium',
    pwd: 'REDACTED_DBZ_CREDENTIAL',
    roles: [
      { role: 'readAnyDatabase', db: 'admin' },
      { role: 'clusterMonitor', db: 'admin' },
      { role: 'read', db: 'local' }
    ]
  });
  print("Debezium user created.");
}

print("MongoDB CDC initialization completed successfully.");