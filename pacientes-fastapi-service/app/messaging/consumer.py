"""Consumidor de los eventos que publican los microservicios Java.

Este servicio no le pregunta nada a nadie: se suscribe a los topics de Kafka y
reacciona a lo que llega. Con esos eventos mantiene dos colecciones propias:

  - fastapi_pacientes:      réplica de los pacientes, para mostrar sus datos sin
                            llamar a pacientes-service.
  - fastapi_notificaciones: un aviso por cada registro médico nuevo, que el
                            paciente consulta en /api/consultas/notificaciones.

Se usa aiokafka (y no kafka-python) porque el resto del servicio es async:
kafka-python es bloqueante y congelaría el event loop de FastAPI. Es la misma
razón por la que en la rama de RabbitMQ se eligió aio-pika sobre pika.
"""

import asyncio
import json
import logging
from datetime import datetime, timezone

from aiokafka import AIOKafkaConsumer
from aiokafka.admin import AIOKafkaAdminClient, NewTopic

from app.core.config import settings
from app.db.mongodb import get_mongo_db
from app.messaging import topology

logger = logging.getLogger(__name__)

_consumidor: AIOKafkaConsumer | None = None
_tarea: asyncio.Task | None = None


async def _asegurar_topics() -> None:
    """Crea los topics si no existen, con los MISMOS argumentos que el lado Java.

    Crear un topic que ya existe lanza TopicAlreadyExistsError, que aquí se
    ignora: la operación es idempotente a propósito, igual que la declaración de
    colas en la rama de RabbitMQ.
    """
    admin = AIOKafkaAdminClient(bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS)
    await admin.start()
    try:
        nuevos = [
            NewTopic(
                name=nombre,
                num_partitions=topology.PARTICIONES,
                replication_factor=topology.REPLICAS,
                topic_configs=topology.CONFIG_TOPICS,
            )
            for nombre in (topology.TOPIC_PACIENTES, topology.TOPIC_HISTORIALES)
        ]
        try:
            await admin.create_topics(nuevos)
            logger.info("Topics asegurados: '%s' y '%s'",
                        topology.TOPIC_PACIENTES, topology.TOPIC_HISTORIALES)
        except Exception as e:
            # Lo normal es que ya existan porque los creó un servicio Java.
            logger.debug("Los topics ya existían (%s)", type(e).__name__)
    finally:
        await admin.close()


def _deserializar(raw: bytes | None):
    """Convierte el cuerpo del mensaje a dict.

    Devuelve None si el mensaje es un "tombstone" (cuerpo nulo), que es como los
    servicios Java señalan un borrado en un topic compactado: la única
    información es la clave del mensaje.
    """
    if raw is None:
        return None
    return json.loads(raw.decode())


async def iniciar_consumidor(intentos: int = 15, espera: int = 4) -> bool:
    """Crea el consumidor y lanza el bucle de lectura en segundo plano.

    Reintenta mientras Kafka arranca: Docker Compose puede levantar este
    contenedor antes de que el broker acepte conexiones. Devuelve False si no se
    logró conectar; el servicio arranca igualmente en modo degradado y sus
    endpoints REST siguen funcionando.
    """
    global _consumidor, _tarea

    for intento in range(1, intentos + 1):
        try:
            # Antes de suscribirse: si los topics no existen todavía, aiokafka
            # entra en un bucle de "Topic not found in cluster metadata" que
            # llena el log con miles de líneas por segundo.
            await _asegurar_topics()

            _consumidor = AIOKafkaConsumer(
                topology.TOPIC_PACIENTES,
                topology.TOPIC_HISTORIALES,
                bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
                group_id=topology.GRUPO,
                # earliest: si este grupo es nuevo, empieza por el offset 0 y
                # reproduce TODA la historia. Así la réplica local se construye
                # completa aunque el servicio se instale mucho después que los
                # demás. Con una cola de RabbitMQ esto era imposible: los
                # mensajes ya consumidos habían desaparecido del broker.
                auto_offset_reset="earliest",
                # Los offsets se confirman a mano, después de procesar. Si el
                # proceso muere a mitad, el mensaje se vuelve a entregar.
                enable_auto_commit=False,
                value_deserializer=_deserializar,
                key_deserializer=lambda k: k.decode() if k else None,
            )
            await _consumidor.start()

            _tarea = asyncio.create_task(_bucle())

            logger.info("Consumidor de Kafka escuchando '%s' y '%s' (grupo '%s')",
                        topology.TOPIC_PACIENTES, topology.TOPIC_HISTORIALES, topology.GRUPO)
            return True

        except Exception as e:
            logger.warning("Kafka no disponible (intento %d/%d): %s", intento, intentos, e)
            await _descartar_consumidor_fallido()
            if intento == intentos:
                logger.error("Se agotaron los intentos de conexión a Kafka; "
                             "el servicio arranca sin mensajería")
                return False
            await asyncio.sleep(espera)

    return False


async def _descartar_consumidor_fallido() -> None:
    """Cierra un consumidor a medio inicializar sin propagar errores."""
    global _consumidor

    if _consumidor is not None:
        try:
            await _consumidor.stop()
        except Exception:
            logger.debug("No se pudo cerrar el consumidor fallido; se descarta igual")
    _consumidor = None


async def _bucle() -> None:
    """Lee mensajes hasta que se cancele la tarea en el shutdown."""
    try:
        async for mensaje in _consumidor:
            try:
                await _procesar(mensaje)
            except Exception as e:
                # Se registra y se sigue. Aquí NO se puede dejar de confirmar el
                # offset: en Kafka el offset es un único número que avanza en
                # orden, así que quedarse en un mensaje que siempre falla
                # bloquearía toda la partición detrás de él.
                logger.error("Error procesando el mensaje de '%s' offset=%s: %s",
                             mensaje.topic, mensaje.offset, e)
            finally:
                await _consumidor.commit()
    except asyncio.CancelledError:
        logger.info("Bucle del consumidor cancelado")
        raise


async def _procesar(mensaje) -> None:
    clave = mensaje.key
    evento = mensaje.value

    logger.debug("Mensaje de '%s' particion=%s offset=%s clave=%s",
                 mensaje.topic, mensaje.partition, mensaje.offset, clave)

    db = get_mongo_db()
    if db is None:
        raise RuntimeError("MongoDB no inicializado, no se puede procesar el evento")

    if clave is None:
        logger.warning("Mensaje sin clave en '%s', se descarta", mensaje.topic)
        return

    # Cuerpo nulo = tombstone = la entidad dejó de existir.
    if evento is None:
        if mensaje.topic == topology.TOPIC_PACIENTES:
            await _eliminar_paciente(db, clave)
        else:
            await _eliminar_notificacion(db, clave)
        return

    tipo = evento.get("tipo")

    if tipo in (topology.EV_PACIENTE_CREADO, topology.EV_PACIENTE_ACTUALIZADO):
        await _guardar_paciente(db, clave, evento)
    elif tipo in (topology.EV_HISTORIAL_CREADO, topology.EV_HISTORIAL_ACTUALIZADO):
        await _crear_notificacion(db, clave, evento, tipo)
    else:
        logger.warning("Tipo de evento no reconocido: %s", tipo)


async def _guardar_paciente(db, clave: str, evento: dict) -> None:
    """Inserta o actualiza la réplica local del paciente.

    upsert=True hace la operación idempotente: reprocesar el mismo evento deja
    el mismo resultado. Importa mucho en Kafka, porque rebobinar el grupo al
    offset 0 vuelve a entregar todos los eventos y la réplica debe reconstruirse
    igual, no duplicarse.
    """
    await db[topology.COLECCION_PACIENTES].update_one(
        {"_id": int(clave)},
        {"$set": {
            "nombre": evento.get("nombre"),
            "apellido": evento.get("apellido"),
            "email": evento.get("email"),
            "telefono": evento.get("telefono"),
            "direccion": evento.get("direccion"),
            "tipoDocumento": evento.get("tipoDocumento"),
            "numeroDocumento": evento.get("numeroDocumento"),
            "fechaSincronizacion": datetime.now(timezone.utc).isoformat(),
        }},
        upsert=True,
    )
    logger.debug("Réplica del paciente %s actualizada", clave)


async def _eliminar_paciente(db, clave: str) -> None:
    paciente_id = int(clave)
    await db[topology.COLECCION_PACIENTES].delete_one({"_id": paciente_id})
    await db[topology.COLECCION_NOTIFICACIONES].delete_many({"pacienteId": paciente_id})
    logger.debug("Réplica y notificaciones del paciente %s eliminadas", paciente_id)


async def _crear_notificacion(db, clave: str, evento: dict, tipo: str) -> None:
    """Crea el aviso de que el paciente tiene un registro médico nuevo.

    Se usa el historialId (la clave del mensaje) como _id, así que un
    "historial.actualizado" reemplaza el aviso anterior en lugar de acumular
    duplicados.
    """
    mensaje = ("Nuevo registro médico disponible" if tipo == topology.EV_HISTORIAL_CREADO
               else "Un registro médico fue actualizado")

    await db[topology.COLECCION_NOTIFICACIONES].update_one(
        {"_id": clave},
        {"$set": {
            "pacienteId": evento.get("pacienteId"),
            "mensaje": mensaje,
            "diagnostico": evento.get("diagnostico"),
            "medico": evento.get("medico"),
            "tipoConsulta": evento.get("tipoConsulta"),
            "fechaConsulta": evento.get("fechaConsulta"),
            "ocurridoEn": evento.get("ocurridoEn"),
            "leida": False,
        }},
        upsert=True,
    )
    logger.debug("Notificación creada para el historial %s", clave)


async def _eliminar_notificacion(db, clave: str) -> None:
    await db[topology.COLECCION_NOTIFICACIONES].delete_one({"_id": clave})
    logger.debug("Notificación del historial %s eliminada", clave)


def esta_conectado() -> bool:
    return _consumidor is not None


async def cerrar() -> None:
    """Para el consumidor en el shutdown para no dejar el grupo colgando."""
    global _consumidor, _tarea

    if _tarea is not None:
        _tarea.cancel()
        try:
            await _tarea
        except asyncio.CancelledError:
            pass
        _tarea = None

    if _consumidor is not None:
        await _consumidor.stop()
        logger.info("Consumidor de Kafka cerrado")
        _consumidor = None
