mvn deploy:deploy-file -DgroupId=io.mateu \
  -DartifactId=lombok \
  -Dversion=1.18.11.44 \
  -Dpackaging=jar \
  -Dfile=dist/lombok-1.18.11.jar \
  -DrepositoryId=mateu-central \
  -Durl=http://nexus.mateu.io/repository/mateu-central/