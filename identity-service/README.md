1. create container mysql:
   docker run --name DBMS_TP_IM -p 3306:3306 -e MYSQL_ROOT_PASSWORD=REDACTED_LEGACY_CREDENTIAL -d mysql:9.3.0
2. create git:
- git init
- git add .
- git commit -m "...."
- git remote add origin http:....