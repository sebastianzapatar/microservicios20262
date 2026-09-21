#!/usr/bin/env bash
#
# Borra el despliegue. Dos niveles:
#
#   ./scripts/k8s-clean.sh          borra el namespace 'salud' (Pods, Services,
#                                   PVCs, ConfigMaps y Secrets). El clúster de
#                                   Minikube y las imágenes se quedan, así que
#                                   volver a desplegar es rápido.
#
#   ./scripts/k8s-clean.sh --todo   además destruye el clúster de Minikube.
#                                   La próxima vez hay que reconstruir las 5
#                                   imágenes desde cero (varios minutos).
#
set -euo pipefail

NS=salud

echo "==> Borrando el namespace '$NS' (esto elimina también los volúmenes de las 3 bases)"
kubectl delete namespace "$NS" --ignore-not-found --wait=true

# El namespace predeterminado del contexto apuntaba a 'salud', que ya no existe.
kubectl config set-context --current --namespace=default >/dev/null 2>&1 || true

if [ "${1:-}" = "--todo" ]; then
  echo "==> Destruyendo el clúster de Minikube"
  minikube delete
fi

echo "==> Listo."
