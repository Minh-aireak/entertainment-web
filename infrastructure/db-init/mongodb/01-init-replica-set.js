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

// Create or rotate the Debezium user with least-privilege CDC roles.
// Credentials are injected at runtime; never store them in this tracked script.
var debeziumUsername = process.env.DEBEZIUM_DB_USERNAME;
var debeziumPassword = process.env.DEBEZIUM_DB_PASSWORD;
var debeziumRoles = [
  { role: 'readAnyDatabase', db: 'admin' },
  { role: 'clusterMonitor', db: 'admin' },
  { role: 'read', db: 'local' }
];

if (!debeziumUsername || !debeziumPassword) {
  print('ERROR: DEBEZIUM_DB_USERNAME and DEBEZIUM_DB_PASSWORD are required.');
  quit(1);
}

print("Checking/Creating Debezium user...");
var adminDb = db.getSiblingDB('admin');
var existing = adminDb.getUser(debeziumUsername);
if (existing) {
  adminDb.updateUser(debeziumUsername, {
    pwd: debeziumPassword,
    roles: debeziumRoles
  });
  print("Debezium user credentials and roles updated.");
} else {
  adminDb.createUser({
    user: debeziumUsername,
    pwd: debeziumPassword,
    roles: debeziumRoles
  });
  print("Debezium user created.");
}

print("MongoDB CDC initialization completed successfully.");
