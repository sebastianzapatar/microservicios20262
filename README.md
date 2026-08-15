# Sistema de Salud - Arquitectura de Microservicios 🏥

Este proyecto es un sistema de salud construido con una **Arquitectura de Microservicios** moderna, combinando tecnologías de la JVM (Java/Spring Boot) y Python (FastAPI). Está diseñado como un proyecto educativo para enseñar cómo orquestar múltiples servicios, bases de datos y sistemas de seguridad.

---

## 🏗️ Arquitectura del Sistema

El ecosistema está compuesto por **5 microservicios**, **3 bases de datos** y un **Identity Provider** (Keycloak). Todos los servicios se descubren de forma dinámica gracias a Eureka y todas las peticiones externas pasan por un único punto de entrada: el API Gateway.

### Los 5 Servicios
1. **📡 Eureka Server (`puerto 8761`)**: Registro y descubrimiento automático de servicios.
2. **🌐 API Gateway (`puerto 8090`)**: Enrutador central y validador primario de seguridad. Todas las peticiones al sistema deben entrar por aquí.
3. **🏥 Pacientes Service (`puerto 8081`)**: Desarrollado en Java (Spring Boot + Data JPA). Realiza el CRUD de pacientes usando **PostgreSQL**.
4. **📋 Historial Médico (`puerto 8082`)**: Desarrollado en Java (Spring Boot + MongoDB). Almacena los historiales clínicos flexibles en **MongoDB**.
5. **🐍 Usuarios FastAPI (`puerto 8083`)**: Desarrollado en Python. Gestiona el registro y login emitiendo tokens JWT propios, y almacena a los usuarios en **MySQL**. Además, permite consultar el historial médico en MongoDB.

### Bases de Datos, Mensajería y Seguridad
- **PostgreSQL**: Datos transaccionales estructurados (Pacientes).
- **MongoDB**: Documentos no relacionales flexibles (Historiales Médicos).
- **MySQL**: Credenciales y registros de usuarios (FastAPI).
- **Apache Kafka (`puerto 9094`)**: Log de eventos por el que se comunican los tres
  servicios de negocio. Consola web en el `puerto 8091`.
- **Keycloak (`puerto 8080`)**: Servidor centralizado para emisión de tokens OAuth2/OIDC.

### Librerías de mensajería
| Servicio | Lenguaje | Librería | Por qué esa |
| :--- | :--- | :--- | :--- |
| Pacientes · Historial | Java | `spring-boot-starter-kafka` | Aporta `KafkaTemplate` y `@KafkaListener`. **Tiene que ser el starter**: en Spring Boot 4 la librería `spring-kafka` suelta no trae la autoconfiguración |
| FastAPI | Python | `aiokafka` | Cliente asíncrono. `kafka-python` es bloqueante y congelaría el event loop |

---

## 🌊 Comunicación por eventos con Apache Kafka

En esta rama los microservicios **ya no se llaman entre sí por HTTP**. Se comunican
publicando y leyendo eventos en Kafka, y las clases `HistorialMedicoClient` y
`PacienteClient` (que resolvían la URL del otro servicio con Eureka) desaparecieron.
Eureka sigue existiendo, pero solo para que el Gateway sepa enrutar.

### La idea que lo explica todo

En una **cola** (RabbitMQ) el mensaje se **borra** cuando alguien lo consume.
En Kafka el mensaje se queda en un **log** y cada consumidor lleva su propio
marcador (*offset*) de por dónde va. Nadie vacía nada.

De ahí salen todas las diferencias:

| | Cola (RabbitMQ) | Log (Kafka) |
| :--- | :--- | :--- |
| Al consumir | El mensaje se borra | Se queda; solo avanza tu offset |
| Copia por servicio | Cada uno necesita **su cola** | Todos leen **el mismo topic**, con distinto *consumer group* |
| Un servicio nuevo | Solo ve lo que llegue a partir de ahora | Puede leer **desde el offset 0** y ponerse al día con todo |
| Orden garantizado | Por cola | Por **partición**, y la clave del mensaje elige la partición |
| Reprocesar | Imposible, ya no están | Se rebobina el grupo y se vuelve a leer |

### Topics

| Topic | Quién escribe | Quién lee | Clave | Política |
| :--- | :--- | :--- | :--- | :--- |
| `salud.pacientes` | Pacientes | Historial · FastAPI | `pacienteId` | `compact` |
| `salud.historiales` | Historial | Pacientes · FastAPI | `historialId` | `compact` |
| `salud.*-dlt` | El error handler | (inspección manual) | — | 7 días |

Los topics de negocio son **compactados**: Kafka conserva para siempre el último
mensaje de cada clave, así que el topic funciona además como una *tabla* con el
estado actual de cada entidad. Los borrados se publican como **tombstone** (un
mensaje con la clave y el cuerpo `null`), que es la forma canónica de eliminar
una clave de un topic compactado.

### Por qué esta rama NO tiene RPC

La rama `rabbitmq` implementaba **dos** patrones: eventos y RPC (petición/respuesta
por cola). El RPC hacía falta porque la cola arrancaba vacía: los pacientes creados
antes de que existiera la cola nunca llegaban, así que el servicio de historiales
necesitaba un respaldo para preguntar por ellos.

Con Kafka ese problema no existe. El log está entero y compactado, y los consumidores
arrancan con `auto-offset-reset: earliest`, así que al primer arranque reconstruyen su
réplica con **toda** la historia. No hay a quién preguntar porque ya lo tienes todo.
Kafka empuja hacia el *event-carried state transfer*, y forzarle un request/reply
encima sería ir contra su diseño.

### Cada servicio y su réplica local

- **Pacientes** consume `salud.historiales` → tabla `historial_resumen` (PostgreSQL).
  Sirve `/api/pacientes/{id}/historial` **sin llamar a nadie**.
- **Historial** consume `salud.pacientes` → colección `pacientes_replica` (MongoDB).
  Valida "¿este paciente existe?" contra su propia base.
- **FastAPI** consume **los dos** → `fastapi_pacientes` y `fastapi_notificaciones`.

### Verlo funcionando

```bash
# Consola web: topics, particiones, mensajes y lag de cada grupo
open http://localhost:8091

# Los topics y su configuración
docker exec kafka-salud /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --describe --topic salud.pacientes

# Los tres consumer groups, cada uno con sus propios offsets
docker exec kafka-salud /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --list

# El contenido del log, tombstones incluidos (valor null)
docker exec kafka-salud /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic salud.historiales \
  --from-beginning --property print.key=true --timeout-ms 5000
```

**La demo que mejor se ve en clase** — reconstruir una réplica desde cero:

```bash
docker stop pacientes-service                       # el grupo debe quedar sin miembros
docker exec postgres-salud psql -U admin -d pacientes_db -c "truncate table historial_resumen;"
docker exec kafka-salud /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --group pacientes-service \
  --topic salud.historiales --reset-offsets --to-earliest --execute
docker start pacientes-service                      # reproduce el log y se rehace sola
```

La tabla se reconstruye reproduciendo el log. Con una cola esto sería imposible:
los mensajes ya consumidos habrían desaparecido del broker.

---

## 🚀 Cómo Correr el Proyecto

El proyecto está completamente contenerizado usando Docker Compose. Esto significa que con un solo comando puedes levantar las 3 bases de datos, Keycloak y la red interna.

### Requisitos Previos
- Docker y Docker Compose instalados.

### Paso a paso

1. **Clonar o descargar** este repositorio y abrir una terminal en la carpeta principal.
2. **Crear el archivo de variables de entorno** a partir de la plantilla y completar los valores vacíos (usuarios y contraseñas):
   ```bash
   cp env.template .env
   ```
3. **Construir y levantar todo el sistema** (3 bases de datos + Keycloak + los 5 microservicios):
   ```bash
   docker compose up --build -d
   ```
   *La primera vez tarda varios minutos: Gradle descarga las dependencias de Spring Boot dentro de cada imagen.*
4. **Verificar que todo esté corriendo**:
   ```bash
   docker compose ps
   ```
   - Abre `http://localhost:8761` para ver el panel de Eureka.
   - Deberías ver registrados `GATEWAY-SERVICE`, `PACIENTES-SERVICE`, `HISTORIAL-MEDICO-SERVICE` y `PACIENTES-FASTAPI-SERVICE`.
   - El panel de administración de Keycloak está en `http://localhost:8080`.

---

## 🧪 Cómo Probar el Flujo Completo

Todo entra por el Gateway (**puerto 8090**). El sistema tiene **dos esquemas de autenticación**, a propósito:

| Endpoints | Quién emite el token | Quién lo valida |
| :--- | :--- | :--- |
| `/api/pacientes/**`, `/api/historiales/**` | **Keycloak** (OAuth2 / OIDC) | El Gateway **y** cada microservicio |
| `/api/usuarios/**`, `/api/consultas/**` | **FastAPI** (JWT propio, HS256) | Solo el microservicio Python |

### A. Flujo con Keycloak (servicios Java)

**1. Obtener un token** (usuarios de prueba: `medico/medico123` o `admin/admin123`):
```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8080/realms/salud-system/protocol/openid-connect/token" \
  -d "client_id=microservicios-client" \
  -d "client_secret=microservicios-secret" \
  -d "username=medico" -d "password=medico123" \
  -d "grant_type=password" | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
```

**2. Crear un paciente** (PostgreSQL):
```bash
curl -X POST "http://localhost:8090/api/pacientes" \
-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
-d '{"nombre":"Ana","apellido":"Gomez","email":"ana@salud.com","telefono":"3001234567",
     "fechaNacimiento":"1990-05-20","direccion":"Calle 10","tipoDocumento":"CC","numeroDocumento":"1001"}'
```

**3. Crear un historial médico** (MongoDB). Este endpoint valida que el paciente exista contra su **réplica local**, que se mantiene al día consumiendo el topic `salud.pacientes`. No hay ninguna llamada al otro microservicio:
```bash
curl -X POST "http://localhost:8090/api/historiales" \
-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
-d '{"pacienteId":1,"diagnostico":"Gripe comun","tratamiento":"Reposo",
     "medico":"Dr. Perez","tipoConsulta":"General","notas":"Control en 5 dias"}'
```

**4. Ver la comunicación por eventos en acción** (Pacientes lee su réplica local):
```bash
curl "http://localhost:8090/api/pacientes/1/historial" -H "Authorization: Bearer $TOKEN"
```
La respuesta combina datos de **PostgreSQL** con el historial que llegó por Kafka.
El campo `sincronizadoHasta` indica hasta qué momento llegó la réplica.

> 🧪 **Apaga `historial-medico-service` y vuelve a llamar a este endpoint.**
> Sigue respondiendo con todos los datos, porque ya no depende de que el otro
> servicio esté vivo. Eso es lo que se gana al desacoplar por eventos.

### B. Flujo con el JWT propio de FastAPI

**1. Crear un nuevo usuario (FastAPI + MySQL)**
```bash
curl -X POST "http://localhost:8090/api/usuarios/registro" \
-H "Content-Type: application/json" \
-d '{"nombre":"Estudiante","email":"estudiante@salud.com","password":"123"}'
```

**2. Iniciar Sesión para obtener el JWT**
```bash
curl -X POST "http://localhost:8090/api/usuarios/login" \
-H "Content-Type: application/json" \
-d '{"email":"estudiante@salud.com","password":"123"}'
```
*(Copia el valor del `access_token` que devuelve este comando).*

**3. Consultar tu información (Requiere Token)**
```bash
curl -X GET "http://localhost:8090/api/consultas/historial" \
-H "Authorization: Bearer AQUI_TU_TOKEN_COPIADO"
```

### Apagar el sistema
```bash
docker compose down          # detiene los contenedores
docker compose down -v       # además borra los volúmenes (datos de las 3 bases)
```

---

## ☸️ Desplegar en Kubernetes

La carpeta `k8s/` contiene los manifiestos para llevar el mismo sistema a un clúster de Minikube.
El paso a paso completo está en **`tutorial_kubernetes.md`** (Parte B).

```bash
minikube start --cpus=4 --memory=8192
eval $(minikube docker-env)          # ⚠️ imprescindible antes de construir
# ... construir las 5 imágenes ...
kubectl create configmap keycloak-realm-config --from-file=realm-export.json=keycloak/realm-export.json
kubectl apply -f k8s/
```

---

## 📚 Material Complementario para Clases

- **`index.html`**: Presentación interactiva con diagramas de arquitectura, flujo de conexión y el stack tecnológico exacto utilizado. ¡Solo ábrelo en cualquier navegador!
- **`kubernetes.html`**: Presentación de Kubernetes (teoría + demo del despliegue de este proyecto). Enlazada desde `index.html`.
- **`tutorial_kubernetes.md`**: Tutorial práctico. Parte A: fundamentos con Minikube. Parte B: desplegar este sistema completo en el clúster.
- **`guia_clase.md`**: Las dependencias exactas que los estudiantes deben instalar en `start.spring.io` o con `uv` para replicar este proyecto desde cero, más las trampas de Spring Boot 4.
- **`k8s_presentacion.md`**: Guion en texto de la presentación de Kubernetes.
- **`resumen_arquitectura.md`**: Detalle técnico del refactor y organización de código.
