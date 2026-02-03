##
## NOTE: Must be run from the root of the project,
## otherwise the war file COPY command will not work!
##
FROM tomee:10-jre21-Temurin-ubuntu-microprofile

ARG TAG="NO-TAG"
ARG NS_VERSION=${TAG}
ARG UID=56700
ARG GID=56700

WORKDIR /usr/local/tomee

RUN groupadd -g $GID notifier && useradd -u $UID -g $GID notifier

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

COPY ./ns-web-api/target/ns-web-api-${NS_VERSION}.war ./webapps/notifications.war

RUN chown -R notifier:notifier .

USER $UID

# metadata
LABEL org.opencontainers.image.title="Notifications"
LABEL org.opencontainers.image.version=${TAG}
LABEL org.opencontainers.image.source="https://github.com/dataoneorg/notification-service"

EXPOSE 8080
EXPOSE 5005

CMD ["catalina.sh", "run"]
