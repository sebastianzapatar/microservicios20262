"""Nombres de la topología de RabbitMQ.

Estos valores DEBEN coincidir con las constantes de RabbitMQConfig.java en
pacientes-service y historial-medico-service. Son un contrato entre lenguajes:
aquí no hay compilador que avise si se escriben mal, el síntoma es una cola que
nunca recibe nada.

Los exchanges se declaran con los MISMOS argumentos que en Java (durable=True,
auto_delete=False). Si dos servicios declaran el mismo exchange con argumentos
distintos, RabbitMQ cierra el canal con PRECONDITION_FAILED.
"""

# ---------- Exchanges (compartidos con los servicios Java) ----------
EXCHANGE_EVENTOS = "salud.events"
EXCHANGE_RPC = "salud.rpc"
EXCHANGE_DLX = "salud.dlx"

# ---------- Colas ----------
# Cola propia de este servicio. Nombre distinto al de los demás: cada consumidor
# necesita SU cola para recibir una copia del evento. Si dos servicios
# compartieran cola, RabbitMQ repartiría los mensajes entre ellos en lugar de
# entregárselos a los dos.
QUEUE_EVENTOS = "fastapi.eventos"

# Cola de descarte COMPARTIDA con los servicios Java (su QUEUE_DLQ).
# Este servicio también la declara, y no solo por simetría: un exchange de tipo
# topic descarta en silencio los mensajes que no encajan con ningún binding. Si
# FastAPI arrancara antes que los servicios Java sobre un broker recién creado,
# sus mensajes rechazados irían al DLX, no encontrarían cola y se perderían
# justo donde se supone que se conservan.
QUEUE_DLQ = "salud.dlq"

# ---------- Routing keys ----------
# Este servicio se suscribe a los eventos de los dos servicios Java.
PATRON_EVENTOS_PACIENTES = "paciente.#"
PATRON_EVENTOS_HISTORIAL = "historial.#"

# El "#" captura cualquier routing key: una sola DLQ recoge todos los descartes.
PATRON_DLQ = "#"

# ---------- Argumentos de las colas ----------
# Los mensajes descartados no se guardan para siempre: sin un límite, un bucle
# de mensajes defectuosos acabaría llenando el disco del broker. 24 h da tiempo
# de sobra a inspeccionar la DLQ en clase.
#
# OJO: este valor DEBE coincidir con el TTL_DLQ_MS de RabbitMQConfig.java. Si
# dos servicios declaran la misma cola con argumentos distintos, RabbitMQ cierra
# el canal con PRECONDITION_FAILED.
TTL_DLQ_MS = 24 * 60 * 60 * 1000

# Routing key para preguntar por RPC a pacientes-service.
RK_RPC_PACIENTES = "pacientes.consulta"

# ---------- Colecciones de MongoDB ----------
# Van prefijadas con "fastapi_" porque este servicio comparte la base
# historial_medico_db con historial-medico-service, que ya tiene su propia
# colección "pacientes_replica". Cada servicio escribe solo en las suyas.
COLECCION_NOTIFICACIONES = "fastapi_notificaciones"
COLECCION_PACIENTES = "fastapi_pacientes"
