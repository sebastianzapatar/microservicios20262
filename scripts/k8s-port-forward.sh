#!/usr/bin/env bash
#
# Abre las tres redirecciones de puertos del clúster hacia tu máquina y las
# mantiene vivas hasta que pulses Ctrl+C.
#
#   localhost:8080 -> Keycloak   (emite los tokens)
#   localhost:8090 -> API Gateway (puerta de entrada al sistema)
#   localhost:8761 -> Eureka      (panel de servicios registrados)
#
# ¿Por qué port-forward y no los NodePort? Keycloak firma el claim "iss" de los
# tokens con la URL pública configurada en k8s/03-keycloak.yaml
# (localhost:8080), y los microservicios comparan ese valor contra
# SPRING_..._ISSUER_URI de k8s/01-config.yaml. Pedir el token por cualquier
# otra URL rompe la coincidencia y todo responde 401.
#
set -euo pipefail

NS=salud
PIDS=()

limpiar() {
  echo ""
  echo "==> Cerrando redirecciones"
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
}
trap limpiar EXIT INT TERM

puerto_ocupado() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

redirigir() {
  local svc=$1 puerto=$2 etiqueta=$3
  if puerto_ocupado "$puerto"; then
    echo "AVISO: el puerto $puerto ya está ocupado ($etiqueta). Libéralo o cierra"
    echo "       lo que esté escuchando ahí (¿docker compose sigue arriba?)."
    return 1
  fi
  kubectl port-forward -n "$NS" "svc/$svc" "$puerto:$puerto" >/dev/null 2>&1 &
  PIDS+=($!)
  echo "  $etiqueta -> http://localhost:$puerto"
}

echo "==> Abriendo redirecciones del namespace '$NS'"
# || true: si un puerto está ocupado se avisa, pero se abren los demás.
redirigir keycloak-salud  8080 "Keycloak   " || true
redirigir gateway-service 8090 "API Gateway" || true
redirigir eureka-server   8761 "Eureka     " || true

echo ""
echo "Listo. Deja esta terminal abierta y usa otra para las pruebas:"
echo "    ./scripts/k8s-test.sh"
echo ""
echo "Ctrl+C para cerrar."
wait
