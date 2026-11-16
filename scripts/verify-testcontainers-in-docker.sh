#!/bin/sh
# Linux local-daemon runner; project and Docker socket are mounted only into the test container.
set -eu
workspace=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
docker_endpoint=${DOCKER_HOST:-unix:///var/run/docker.sock}
case "$docker_endpoint" in
  unix://*) socket_path=${docker_endpoint#unix://} ;;
  *) printf '%s\n' 'This runner requires a local Linux Unix-socket daemon.' >&2; exit 1 ;;
esac
test -S "$socket_path"
gateway=${TESTCONTAINERS_HOST_OVERRIDE:-$(docker network inspect bridge --format '{{(index .IPAM.Config 0).Gateway}}')}
set --
if [ -n "${MAVEN_REPOSITORY:-}" ]; then
  test -d "$MAVEN_REPOSITORY"
  set -- -v "$MAVEN_REPOSITORY:/root/.m2/repository"
fi
docker run --rm \
  "$@" \
  -v "$workspace/backend:/workspace" \
  -v "$workspace/scripts:/scripts:ro" \
  -v "$socket_path:/var/run/docker.sock" \
  -e TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="$socket_path" \
  -e TESTCONTAINERS_HOST_OVERRIDE="$gateway" \
  -w /workspace maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 mvn -B verify -Ptestcontainers
