# Notification service

# Mongodb
Install Mongodb from Docker hub

'docker pull bitnami/mongodb:8.0.10'

Start mongo service at port 27017 username:root password:1801062012

'docker run -d --name mongodb-8.0.10 -p 27017:27017 -e MONGODB_ROOT_USER=root -e MONGODB_ROOT_PASSWORD=1801062012 bitnami/mongodb:8.0.10'