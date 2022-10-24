scp build/libs/HomeController-0.0.1-SNAPSHOT.jar dev@192.168.0.34:/home/dev/app/HomeController-0.0.1-SNAPSHOT.jar
ssh dev@192.168.0.34 /home/dev/app/run-home-portal.sh
echo "App deployed and starting"
