"""Consumidor de los eventos que publican los microservicios Java.

Este es el lado asíncrono del patrón: FastAPI no le pregunta nada a nadie, se
suscribe a la cola "fastapi.eventos" y reacciona a lo que llega.

Con los eventos mantiene dos colecciones propias en MongoDB:
  - fastapi_pacientes:      réplica de los pacientes, para mostrar nombres sin
                            tener que preguntar por RPC.
  - fastapi_notificaciones: un aviso por cada registro médico nuevo, que el
                            paciente puede consultar en /api/consultas/notificaciones.
"""

import json
import logging
from datetime import datetime, timezone

from aio_pika.abc import AbstractIncomingMessage

from app.db.mongodb import get_mongo_db
from app.messaging import broker, topology

logger = logging.getLogger(__name__)


async def iniciar_consumidor() -> bool:
    """Engancha el callback a la cola. Devuelve False si no hay conexión."""
    channel = broker.get_channel()
    if channel is None:
        logger.warning("Sin canal de RabbitMQ; el consumidor de eventos no arranca")
        return False

    cola = await channel.get_queue(topology.QUEUE_EVENTOS)
    await cola.consume(_procesar)

    logger.info("Consumidor escuchando la cola '%s'", topology.QUEUE_EVENTOS)
    return True


async def _procesar(mensaje: AbstractIncomingMessage) -> None:
    """Procesa un mensaje de la cola.

    El bloque "async with mensaje.process()" confirma el mensaje (ack) al salir
    sin excepción, y lo rechaza si algo falla. requeue=False para que un mensaje
    defectuoso no vuelva a la cola en bucle: se va al DLX que se declaró en
    broker.declarar_topologia.
    """
    async with mensaje.process(requeue=False):
        try:
            evento = json.loads(mensaje.body.decode())
        except json.JSONDecodeError as e:
            logger.error("Mensaje con JSON inválido, se descarta: %s", e)
            return

        # El "tipo" viene en el cuerpo (lo pone el publicador Java) y repite la
        # routing key. Se usa el del cuerpo para no depender de las cabeceras AMQP.
        tipo = evento.get("tipo") or mensaje.routing_key
        logger.debug("Evento '%s' recibido", tipo)

        db = get_mongo_db()
        if db is None:
            # Si Mongo no está listo, se propaga: el mensaje se rechaza y el DLX
            # lo conserva en lugar de perderlo silenciosamente.
            raise RuntimeError("MongoDB no inicializado, no se puede procesar el evento")

        if tipo in ("paciente.creado", "paciente.actualizado"):
            await _guardar_paciente(db, evento)
        elif tipo == "paciente.eliminado":
            await _eliminar_paciente(db, evento)
        elif tipo in ("historial.creado", "historial.actualizado"):
            await _crear_notificacion(db, evento, tipo)
        elif tipo == "historial.eliminado":
            await _eliminar_notificaciones(db, evento)
        else:
            logger.warning("Tipo de evento no reconocido: %s", tipo)


async def _guardar_paciente(db, evento: dict) -> None:
    """Inserta o actualiza la réplica local del paciente.

    upsert=True hace la operación idempotente: reprocesar el mismo evento deja
    el mismo resultado. Importa porque RabbitMQ garantiza "al menos una vez".
    """
    paciente_id = evento.get("pacienteId")
    if paciente_id is None:
        logger.warning("Evento de paciente sin pacienteId, se descarta")
        return

    await db[topology.COLECCION_PACIENTES].update_one(
        {"_id": paciente_id},
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
    logger.debug("Réplica del paciente %s actualizada", paciente_id)


async def _eliminar_paciente(db, evento: dict) -> None:
    paciente_id = evento.get("pacienteId")
    if paciente_id is None:
        return

    await db[topology.COLECCION_PACIENTES].delete_one({"_id": paciente_id})
    await db[topology.COLECCION_NOTIFICACIONES].delete_many({"pacienteId": paciente_id})
    logger.debug("Réplica y notificaciones del paciente %s eliminadas", paciente_id)


async def _crear_notificacion(db, evento: dict, tipo: str) -> None:
    """Crea el aviso de que el paciente tiene un registro médico nuevo.

    Se usa el historialId como _id, así que un "historial.actualizado" reemplaza
    el aviso anterior en lugar de acumular duplicados.
    """
    historial_id = evento.get("historialId")
    if historial_id is None:
        logger.warning("Evento de historial sin historialId, se descarta")
        return

    mensaje = ("Nuevo registro médico disponible" if tipo == "historial.creado"
               else "Un registro médico fue actualizado")

    await db[topology.COLECCION_NOTIFICACIONES].update_one(
        {"_id": historial_id},
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
    logger.debug("Notificación creada para el historial %s", historial_id)


async def _eliminar_notificaciones(db, evento: dict) -> None:
    historial_id = evento.get("historialId")
    if historial_id is None:
        return

    await db[topology.COLECCION_NOTIFICACIONES].delete_one({"_id": historial_id})
    logger.debug("Notificación del historial %s eliminada", historial_id)
