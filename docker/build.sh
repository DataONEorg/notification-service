#!/bin/bash

set -e

# Run from the script's own directory; return to the caller's directory on exit
pushd "$(dirname "${BASH_SOURCE[0]}")" > /dev/null
trap 'popd > /dev/null' EXIT

# Initialize variables
TAG=""
DEFAULT_TAG="LOCAL"
DEFAULT_ARCH="linux/arm64"  # Mac Silicon (since this script commonly used on local machine)
ARCH_NAME="Mac Silicon"
CACHE=""
DEFAULT_NS_VERSION=$(xmllint --xpath \
        "string(/*[local-name()='project']/*[local-name()='properties']/*[local-name()='revision'])" ../pom.xml)
if [ -z "$DEFAULT_NS_VERSION" ]; then
    echo "Failed to populate \$DEFAULT_NS_VERSION from ../pom.xml. Exiting..."
    exit 1
fi

# Function to display usage
usage() {
    echo "Usage: $0 [-t <TAG>] [-v <VERSION>] [-p <PLATFORM>] [-n]"
    echo
    echo "where:  -t <TAG>  is the image tag (typically matches the build version; see below)."
    echo "                  (Defaults to $DEFAULT_TAG if the -t option is omitted.)"
    echo
    echo "        -v <VERSION> is the notification-service build version number (https://semver.org/ format)"
    echo "                  (Defaults to $DEFAULT_NS_VERSION if the -v option is omitted.)"
    echo
    echo "        -p <PLATFORM> is the architecture to build for (e.g. linux/amd64 for linux k8s"
    echo "                  clusters, or linux/arm64 for Mac Silicon)."
    echo "                  (Defaults to $DEFAULT_ARCH ($ARCH_NAME) if the -p option is omitted.)"
    echo
    echo "        -n denotes '--no-cache' mode, forcing docker to rebuild all layers of the image,"
    echo "                  instead of using cached layers. The default is to use cached layers,"
    echo "                  which is faster, but may not reflect recent changes to the source code."
    echo
    exit 1
}

# Parse command-line arguments
while getopts ":p:t:v:-:hn" opt; do
    case "$opt" in
    h) usage ;;
    n) CACHE="--no-cache" ;;
    p) ARCH="$OPTARG" ;;
    t) TAG="$OPTARG" ;;
    v) NS_VERSION="$OPTARG" ;;
    *) usage ;;
    esac
done

if [[ -n $NS_VERSION ]]; then
    echo "Version provided: $NS_VERSION"
else
    NS_VERSION=$DEFAULT_NS_VERSION
    echo "No Version provided. Defaulting to: $DEFAULT_NS_VERSION from top-level pom file"
fi

if [[ -n $TAG ]]; then
    echo "Tag name provided: $TAG"
else
    TAG=$DEFAULT_TAG
    echo "No Tag name provided. Defaulting to: $DEFAULT_TAG"
fi

if [[ -n $ARCH ]]; then
    echo -n "Requested Platform Architecture: ${ARCH}"
    if [[ "$ARCH" == "linux/amd64" ]]; then
      ARCH_NAME="Linux x86_64"
    elif [[ "$ARCH" == "linux/arm64" ]]; then
      ARCH_NAME="Mac Silicon"
    else
      echo " * * * UNRECOGNIZED! * * *  -  Defaulting to $DEFAULT_ARCH"
      ARCH="$DEFAULT_ARCH"
    fi
else
    echo "No Platform Architecture provided. Defaulting to: $DEFAULT_ARCH"
    ARCH=$DEFAULT_ARCH
fi
echo "...building for ${ARCH_NAME}"

echo
echo "* * *  $(date) Starting docker image builds, using:"
echo "  TAG:                    $TAG"
echo "  VERSION:                $NS_VERSION"
echo "  PLATFORM ARCHITECTURE:  $ARCH ($ARCH_NAME)"
echo "*********************************************************************************************"
echo "1. ns-web-api image build..."
echo
docker buildx build \
    --file      Dockerfile-web-api \
    --tag ghcr.io/dataoneorg/notification-service:"$TAG" \
    --platform  "$ARCH" \
    --build-arg TAG="$TAG" \
    --build-arg NS_VERSION="$NS_VERSION" \
    $CACHE ..
echo
echo "*********************************************************************************************"
echo "2. ns-consumer image build..."
echo
docker buildx build \
    --file      Dockerfile-consumer \
    --tag ghcr.io/dataoneorg/notification-consumer:"$TAG" \
    --platform  "$ARCH" \
    --build-arg TAG="$TAG" \
    --build-arg NS_VERSION="$NS_VERSION" \
    $CACHE ..
