1. create container mysql:
   docker run --name DBMS_TP_IM -p 3306:3306 -e MYSQL_ROOT_PASSWORD=1801062012 -d mysql:9.3.0
2. create git:
- git init
- git add .
- git commit -m "...."
- git remote add origin http:....