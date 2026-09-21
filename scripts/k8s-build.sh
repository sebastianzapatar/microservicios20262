#!/usr/bin/env bash
#
# Construye las 5 imágenes del proyecto DENTRO del daemon Docker de Minikube.
#
# ¿Por qué dentro de Minikube? Porque los manifiestos usan
# `imagePullPolicy: Never`: el clúster no baja nada de internet, usa solo las
# imágenes que ya existen en su propio nodo. Si construyes en el Docker de tu
# Mac/PC, Minikube no las ve y los Pods fallan con
# "Container image ... is not present with pull policy of Never".
#
# Uso:  ./scripts/k8s-build.sh            (construye las 5)
#       ./scripts/k8s-build.sh gateway-service pacientes-service   (solo esas)
#
set -euo pipefail

# Todos los comandos se ejecutan desde la raíz del proyecto, sin importar
# desde dónde se invoque el script.
cd "$(dirname "$0")/.."

TODAS=(eureka-server gateway-service pacientes-service historial-medico-service pacientes-fastapi-service)
if [ $# -gt 0 ]; then
  SERVICIOS=("$@")
else
  SERVICIOS=("${TODAS[@]}")
fi

echo "==> Apuntando el cliente Docker al daemon de Minikube"
if ! minikube status >/dev/null 2>&1; then
  echo "ERROR: Minikube no está corriendo. Arráncalo con:" >&2
  echo "       minikube start --cpus=4 --memory=8192" >&2
  exit 1
fi
# Solo afecta a ESTE script; tu terminal no queda modificada.
eval "$(minikube -p minikube docker-env)"

for servicio in "${SERVICIOS[@]}"; do
  if [ ! -d "$servicio" ]; then
    echo "ERROR: no existe la carpeta '$servicio'" >&2
    exit 1
  fi
  echo ""
  echo "==> Construyendo $servicio:latest"
  docker build -t "$servicio:latest" "./$servicio"
done

echo ""
echo "==> Imágenes disponibles dentro de Minikube:"
docker images --format '{{.Repository}}:{{.Tag}}\t{{.Size}}' \
  | grep -E '^(eureka-server|gateway-service|pacientes-service|historial-medico-service|pacientes-fastapi-service):' \
  || echo "   (ninguna encontrada)"
