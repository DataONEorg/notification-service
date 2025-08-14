#!/bin/bash

## This script will run a postgres database in a docker container, for development purposes

## Settings

## NOTE: the default postgres password is literally "YOUR-PASSWORD-HERE" (not secure - for
## development only!); if you change it below, you must also change it in properties.yaml!
POSTGRES_PASSWORD=YOUR-PASSWORD-HERE
POSTGRES_USER=notifications_user
POSTGRES_VERSION=17
PORT=5432
CONTAINER_NAME=notifications-db

## Stop and remove the container if it exists
docker stop ${CONTAINER_NAME} >& /dev/null
docker rm ${CONTAINER_NAME} >& /dev/null

## Run the container
docker run --name ${CONTAINER_NAME}         \
  -e POSTGRES_PASSWORD=${POSTGRES_PASSWORD} \
  -e POSTGRES_DB=notifications              \
  -e POSTGRES_USER=${POSTGRES_USER}         \
  -e PGDATA=/tmp/postgresql/data            \
  -e POSTGRES_HOST_AUTH_METHOD=password     \
  -p ${PORT}:5432                           \
  -d postgres:${POSTGRES_VERSION}

echo "Access postgres on localhost port ${PORT}"
echo "Stop the container with \"docker stop ${CONTAINER_NAME}\""
