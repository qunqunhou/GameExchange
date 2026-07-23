@echo off
echo 开始打包上传...

scp C:\Users\23053\IdeaProjects\GameExchange\out\artifacts\GameExchange_war\GameExchange_war.war root@8.166.121.11:/root/

echo 上传完成，开始部署...

ssh root@8.166.121.11 "/opt/tomcat9/bin/shutdown.sh; sleep 3; rm -rf /opt/tomcat9/webapps/GameExchange_war; rm -f /opt/tomcat9/webapps/GameExchange_war.war; mv /root/GameExchange_war.war /opt/tomcat9/webapps/;/opt/tomcat9/bin/startup.sh
"

echo 部署完成！
pause