FROM tomcat:10

COPY ./target/JavaSASTAnalysis-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/

EXPOSE 8080

RUN apt-get update && apt-get install -y \
        python3.4 \
        python3-pip

RUN pip install semgrep

CMD ["catalina.sh", "run"]
