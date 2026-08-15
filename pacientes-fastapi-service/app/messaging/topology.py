"""Nombres de la topología de Kafka.

Estos valores DEBEN coincidir con las constantes de KafkaConfig.java en
pacientes-service y historial-medico-service. Son un contrato entre lenguajes:
aquí no hay compilador que avise si se escriben mal, y el síntoma de un error es
un consumidor que no recibe nada.
"""

# ---------- Topics (compartidos con los servicios Java) ----------
# Este servicio LEE los dos y no escribe en ninguno.
#
# Fíjate en la diferencia con RabbitMQ: allí cada servicio necesitaba SU PROPIA
# cola para recibir una copia de cada mensaje. Aquí los tres servicios leen los
# MISMOS topics; lo que los separa es el consumer group, porque Kafka guarda los
# offsets por grupo y no borra el mensaje al entregarlo.
TOPIC_PACIENTES = "salud.pacientes"
TOPIC_HISTORIALES = "salud.historiales"

# ---------- Consumer group ----------
# Identifica a ESTE servicio como lector. Si se levantan varias instancias con
# el mismo grupo, Kafka les reparte las particiones en lugar de duplicarles el
# trabajo.
GRUPO = "pacientes-fastapi-service"

# ---------- Tipos de evento ----------
# Viajan dentro del cuerpo del mensaje, en el campo "tipo".
EV_PACIENTE_CREADO = "paciente.creado"
EV_PACIENTE_ACTUALIZADO = "paciente.actualizado"
EV_HISTORIAL_CREADO = "historial.creado"
EV_HISTORIAL_ACTUALIZADO = "historial.actualizado"

# ---------- Configuración de los topics ----------
# DEBE coincidir con lo que declaran los KafkaConfig.java. Este servicio también
# los crea, y no solo por simetría: si FastAPI arranca antes que los servicios
# Java (cosa que pasa a menudo con Docker Compose), suscribirse a un topic que
# todavía no existe hace que aiokafka repita "Topic not found in cluster
# metadata" miles de veces por segundo hasta que aparezca. Crearlos aquí evita
# ese ruido y permite que este servicio arranque solo contra un clúster vacío.
PARTICIONES = 3
REPLICAS = 1
CONFIG_TOPICS = {"cleanup.policy": "compact"}

# ---------- Colecciones de MongoDB ----------
# Van prefijadas con "fastapi_" porque este servicio comparte la base
# historial_medico_db con historial-medico-service, que ya tiene su propia
# colección "pacientes_replica". Cada servicio escribe solo en las suyas.
COLECCION_NOTIFICACIONES = "fastapi_notificaciones"
COLECCION_PACIENTES = "fastapi_pacientes"
