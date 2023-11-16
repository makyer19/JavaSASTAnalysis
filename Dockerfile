FROM tomcat:10.0

COPY ./target/JavaSASTAnalysis-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/

EXPOSE 8080

VOLUME scan-dir

RUN apt-get update && apt-get install -y \
        python3.4 \
        python3-pip \
        ca-certificates \
        curl \
        gnupg

RUN install -m 0755 -d /etc/apt/keyrings
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
RUN chmod a+r /etc/apt/keyrings/docker.gpg

# Add the repository to Apt sources:
RUN echo "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian \
  "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

RUN sed -i 's/debian/ubuntu/g' /etc/apt/sources.list.d/docker.list

RUN apt-get update && apt-get install -y \
        docker-ce \
        docker-ce-cli \
        containerd.io \
        docker-buildx-plugin \
        docker-compose-plugin

RUN pip install semgrep

CMD ["catalina.sh", "run"]
