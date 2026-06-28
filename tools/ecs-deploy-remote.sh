#!/usr/bin/env bash
# Runs on ECS: install docker (if needed), login ACR, pull and run plantops container.
set -eu

CONTAINER_NAME="${ECS_CONTAINER_NAME:-plantops}"
HOST_PORT="${ECS_HOST_PORT:-8080}"
DATA_DIR="${ECS_DATA_DIR:-/data/plantops}"
IMAGE="${ACR_IMAGE:?ACR_IMAGE required}"
ACR_REGISTRY="${ACR_REGISTRY:?ACR_REGISTRY required}"
ACR_USERNAME="${ACR_USERNAME:?ACR_USERNAME required}"
ACR_PASSWORD="${ACR_PASSWORD:?ACR_PASSWORD required}"
SAMPLE_DATA="${PLANTOPS_SAMPLE_DATA_ENABLED:-false}"

echo "==> Ensure Docker is installed"
if ! command -v docker >/dev/null 2>&1; then
  if command -v yum >/dev/null 2>&1; then
    yum install -y docker
    systemctl enable docker
    systemctl start docker
  elif command -v apt-get >/dev/null 2>&1; then
    apt-get update -y
    apt-get install -y docker.io
    systemctl enable docker
    systemctl start docker
  else
    echo "Docker not found and no supported package manager (yum/apt-get)." >&2
    exit 1
  fi
fi

echo "==> Prepare data directory: ${DATA_DIR}"
mkdir -p "${DATA_DIR}"
chown -R 10001:10001 "${DATA_DIR}" 2>/dev/null || true
rm -f "${DATA_DIR}/plantops.lock.db" 2>/dev/null || true

echo "==> Login ACR: ${ACR_REGISTRY}"
echo "${ACR_PASSWORD}" | docker login --username "${ACR_USERNAME}" --password-stdin "${ACR_REGISTRY}"

echo "==> Pull ${IMAGE}"
docker pull "${IMAGE}"

if docker ps -a --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  echo "==> Stop and remove existing container: ${CONTAINER_NAME}"
  docker stop "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  docker rm "${CONTAINER_NAME}" >/dev/null 2>&1 || true
fi

echo "==> Run container ${CONTAINER_NAME}"
docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  -p "${HOST_PORT}:8080" \
  -v "${DATA_DIR}:/app/data" \
  -e QUARKUS_PROFILE=docker \
  -e "PLANTOPS_SAMPLE_DATA_ENABLED=${SAMPLE_DATA}" \
  "${IMAGE}"

echo "==> Container status"
docker ps --filter "name=${CONTAINER_NAME}"

echo ""
echo "Deploy OK. Open: http://$(curl -s --max-time 3 http://100.100.100.200/latest/meta-data/eipv4 2>/dev/null || hostname -I | awk '{print $1}'):${HOST_PORT}/#/"
