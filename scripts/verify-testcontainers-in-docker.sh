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
python3 "$workspace/scripts/security_images.py" --build
tool_image=$(python3 "$workspace/scripts/security_images.py" --role maven)
gateway=${TESTCONTAINERS_HOST_OVERRIDE:-$(docker network inspect bridge --format '{{(index .IPAM.Config 0).Gateway}}')}
set --
if [ -n "${MAVEN_REPOSITORY:-}" ]; then
  test -d "$MAVEN_REPOSITORY"
  set -- -v "$MAVEN_REPOSITORY:/root/.m2/repository"
fi
mkdir -p "$workspace/backend/target"
docker run --rm \
  "$@" \
  -v "$workspace/backend:/source:ro" \
  -v "$workspace/backend/target:/reports" \
  -v "$workspace/scripts:/scripts:ro" \
  -v "$socket_path:/var/run/docker.sock" \
  -e TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="$socket_path" \
  -e TESTCONTAINERS_HOST_OVERRIDE="$gateway" \
  -w /workspace "$tool_image" sh -c '
    set -eu
    tar -C /source --exclude=target -cf - . | tar -C /workspace -xf -
    set +e
    mvn -B verify -Ptestcontainers
    result=$?
    set -e
    for report in target/surefire-reports target/failsafe-reports; do
      if [ -d "$report" ]; then cp -R "$report" /reports/; fi
    done
    if [ -f target/scarlet-nexus-0.1.0-SNAPSHOT.jar ]; then
      cp target/scarlet-nexus-0.1.0-SNAPSHOT.jar /reports/
    fi
    exit "$result"
  '
