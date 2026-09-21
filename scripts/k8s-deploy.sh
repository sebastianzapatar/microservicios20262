#!/usr/bin/env bash
#
# Despliega el Sistema de Salud completo en Minikube, de principio a fin:
#   1. Arranca Minikube si hace falta.
#   2. Construye las 5 imágenes dentro del clúster.
#   3. Aplica todos los manifiestos (kubectl apply -k .).
#   4. Espera a que los 9 Deployments estén listos.
#
# Uso:  ./scripts/k8s-deploy.sh
#       ./scripts/k8s-deploy.sh --sin-build      (si las imágenes ya existen)
#
set -euo pipefail
cd "$(dirname "$0")/.."

NS=salud
CONSTRUIR=1
[ "${1:-}" = "--sin-build" ] && CONSTRUIR=0

# --- 1. Clúster ------------------------------------------------------------
if minikube status >/dev/null 2>&1; then
  echo "==> Minikube ya está corriendo"
else
  echo "==> Arrancando Minikube (4 CPUs, 8 GB) — esto tarda un par de minutos"
  minikube start --cpus=4 --memory=8192
fi

# --- 2. Imágenes -----------------------------------------------------------
if [ "$CONSTRUIR" = "1" ]; then
  ./scripts/k8s-build.sh
else
  echo "==> Saltando la construcción de imágenes (--sin-build)"
fi

# --- 3. Manifiestos --------------------------------------------------------
echo ""
echo "==> Aplicando manifiestos (namespace '$NS')"
# -k usa el kustomization.yaml de la raíz: crea el namespace, genera el
# ConfigMap del realm de Keycloak y aplica los 24 objetos en orden.
kubectl apply -k .

# Deja el namespace 'salud' como predeterminado para que el resto de comandos
# (kubectl get pods, logs, port-forward...) no necesiten escribir -n salud.
kubectl config set-context --current --namespace="$NS" >/dev/null
echo "==> Namespace predeterminado de kubectl: $NS"

# --- 4. Esperar ------------------------------------------------------------
echo ""
echo "==> Esperando a que todo esté listo (la primera vez tarda 3-6 minutos)"
echo "    Mientras tanto puedes mirar el progreso en otra terminal con:"
echo "      kubectl get pods -w"
echo ""

# Se espera por capas, en el mismo orden en que el sistema converge.
for capa in \
  "postgres-salud mongo-salud mysql-salud" \
  "keycloak-salud eureka-server" \
  "pacientes-service historial-medico-service pacientes-fastapi-service" \
  "gateway-service"
do
  for dep in $capa; do
    echo "--- esperando deployment/$dep"
    kubectl rollout status "deployment/$dep" --timeout=600s
  done
done

echo ""
kubectl get pods
echo ""
echo "=========================================================="
echo " Sistema desplegado en el namespace '$NS'"
echo ""
echo " Siguiente paso — abrir los puertos hacia tu máquina:"
echo "     ./scripts/k8s-port-forward.sh"
echo ""
echo " Y después, probar el flujo completo:"
echo "     ./scripts/k8s-test.sh"
echo "=========================================================="
