##
## NOTE: Must be run from the root of the project,
## otherwise the war file COPY command will not work!
##
FROM tomee:9-jre17-Temurin-ubuntu-webprofile

ARG TAG=1.0.0-SNAPSHOT
ARG NS_VERSION=${TAG}

WORKDIR /usr/local/tomee

RUN groupadd -g 1000 notifier && useradd -u 1000 -g 1000 notifier

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
    bash              \
    figlet            \
    curl              \
    iputils-ping      \
    lsof              \
    postgresql-client \
    procps            \
    telnet            \
    vim

COPY ../target/notification-service-${NS_VERSION}.war ./webapps/notifications.war

RUN chown -R notifier:notifier .

USER 1000

# metadata
LABEL org.opencontainers.image.title="Notifications"
LABEL org.opencontainers.image.version=${TAG}
LABEL org.opencontainers.image.source="https://github.com/dataoneorg/notification-service"

EXPOSE 8080
EXPOSE 5005

CMD ["catalina.sh", "run"]
