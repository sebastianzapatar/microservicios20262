#!/usr/bin/env bash
#
# Prueba de humo del sistema desplegado en Kubernetes.
# Recorre los dos esquemas de autenticación del proyecto de punta a punta:
#
#   A. Keycloak (servicios Java)  -> token OIDC, crear paciente, crear
#      historial y la llamada entre microservicios vía Eureka.
#   B. JWT propio de FastAPI      -> registro, login y consulta protegida.
#
# REQUISITO: las redirecciones de puertos tienen que estar abiertas en otra
# terminal:   ./scripts/k8s-port-forward.sh
#
set -euo pipefail

# Se pueden sobrescribir si tuviste que redirigir a otros puertos, por ejemplo
# porque Docker Compose sigue ocupando el 8090:
#   GATEWAY=http://localhost:18090 ./scripts/k8s-test.sh
#
# OJO: KEYCLOAK debe seguir siendo localhost:8080. Los microservicios comparan
# el claim "iss" del token contra esa URL exacta; cualquier otra da 401.
KEYCLOAK=${KEYCLOAK:-http://localhost:8080}
GATEWAY=${GATEWAY:-http://localhost:8090}
SUFIJO=$(date +%s)   # hace únicos el documento y el email en cada ejecución

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print(d$1)" 2>/dev/null || true; }

titulo() { echo ""; echo "── $1"; }

fallo() { echo ""; echo "FALLO: $1" >&2; exit 1; }

command -v python3 >/dev/null || fallo "se necesita python3 para leer las respuestas JSON"

echo "=========================================================="
echo " Prueba de humo — Sistema de Salud en Kubernetes"
echo "=========================================================="

titulo "0. Comprobando que las redirecciones están abiertas"
curl -sf "$KEYCLOAK/realms/salud-system/.well-known/openid-configuration" >/dev/null \
  || fallo "Keycloak no responde en $KEYCLOAK. ¿Corriste ./scripts/k8s-port-forward.sh?"
curl -sf "$GATEWAY/actuator/health" >/dev/null \
  || fallo "El Gateway no responde en $GATEWAY. ¿Corriste ./scripts/k8s-port-forward.sh?"
echo "   Keycloak y Gateway responden."

# ---------------------------------------------------------------------------
# A. Flujo con Keycloak
# ---------------------------------------------------------------------------
titulo "1. Pidiendo un token a Keycloak (usuario: medico)"
TOKEN=$(curl -s -X POST \
  "$KEYCLOAK/realms/salud-system/protocol/openid-connect/token" \
  -d "client_id=microservicios-client" \
  -d "client_secret=microservicios-secret" \
  -d "username=medico" \
  -d "password=medico123" \
  -d "grant_type=password" | json "['access_token']")

[ -n "$TOKEN" ] || fallo "Keycloak no devolvió access_token (revisa el realm y las credenciales)"
echo "   Token obtenido: ${TOKEN:0:32}..."

titulo "2. Creando un paciente (Gateway -> PACIENTES-SERVICE -> PostgreSQL)"
PACIENTE=$(curl -s -X POST "$GATEWAY/api/pacientes" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"nombre\":\"Ana\",\"apellido\":\"Gomez\",\"email\":\"ana$SUFIJO@salud.com\",
       \"telefono\":\"3001234567\",\"fechaNacimiento\":\"1990-05-20\",
       \"direccion\":\"Calle 10 #20-30\",\"tipoDocumento\":\"CC\",
       \"numeroDocumento\":\"$SUFIJO\"}")

PACIENTE_ID=$(echo "$PACIENTE" | json "['id']")
[ -n "$PACIENTE_ID" ] || fallo "no se pudo crear el paciente. Respuesta: $PACIENTE"
echo "   Paciente creado con id=$PACIENTE_ID"

titulo "3. Creando un historial médico (Gateway -> HISTORIAL-MEDICO-SERVICE -> MongoDB)"
echo "   (este endpoint valida el paciente llamando al otro microservicio vía Eureka)"
HISTORIAL=$(curl -s -X POST "$GATEWAY/api/historiales" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"pacienteId\":$PACIENTE_ID,\"diagnostico\":\"Gripe comun\",
       \"tratamiento\":\"Reposo e hidratacion\",\"medico\":\"Dr. Perez\",
       \"tipoConsulta\":\"General\",\"notas\":\"Control en 5 dias\"}")

HISTORIAL_ID=$(echo "$HISTORIAL" | json "['id']")
[ -n "$HISTORIAL_ID" ] || fallo "no se pudo crear el historial. Respuesta: $HISTORIAL"
echo "   Historial creado con id=$HISTORIAL_ID"

titulo "4. Comunicación entre microservicios (PostgreSQL + MongoDB en una sola respuesta)"
curl -s "$GATEWAY/api/pacientes/$PACIENTE_ID/historial" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

titulo "5. Verificando que sin token se rechaza la petición"
CODIGO=$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/api/pacientes")
case "$CODIGO" in
  401|403) echo "   Correcto: el Gateway respondió $CODIGO sin token." ;;
  *)       fallo "se esperaba 401/403 sin token, pero llegó $CODIGO" ;;
esac

# ---------------------------------------------------------------------------
# B. Flujo con el JWT propio de FastAPI
# ---------------------------------------------------------------------------
titulo "6. Registrando un usuario (Gateway -> PACIENTES-FASTAPI-SERVICE -> MySQL)"
EMAIL="estudiante$SUFIJO@salud.com"
REGISTRO=$(curl -s -X POST "$GATEWAY/api/usuarios/registro" \
  -H "Content-Type: application/json" \
  -d "{\"nombre\":\"Estudiante\",\"email\":\"$EMAIL\",\"password\":\"123\"}")
echo "   $REGISTRO"

titulo "7. Login en FastAPI (JWT propio, HS256)"
JWT=$(curl -s -X POST "$GATEWAY/api/usuarios/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"123\"}" | json "['access_token']")

[ -n "$JWT" ] || fallo "FastAPI no devolvió access_token"
echo "   JWT obtenido: ${JWT:0:32}..."

titulo "8. Consulta protegida por el JWT de FastAPI (lee el historial de MongoDB)"
curl -s "$GATEWAY/api/consultas/historial" \
  -H "Authorization: Bearer $JWT" | python3 -m json.tool

echo ""
echo "=========================================================="
echo " Todo el flujo funcionó: 3 bases de datos, 2 esquemas de"
echo " autenticación y una llamada entre microservicios vía Eureka."
echo "=========================================================="
