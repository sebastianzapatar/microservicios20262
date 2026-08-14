"""Cliente RPC hacia pacientes-service (Java) por la cola "pacientes.rpc".

Es el lado síncrono del patrón, hecho a mano a propósito. aio_pika.patterns.RPC
existe, pero usa su propio formato de serialización (pickle) y no se entiende con
Spring AMQP. El protocolo request/reply de AMQP es el mismo en los dos lados y
son solo tres piezas:

  1. reply_to        -> la cola donde se quiere la respuesta.
  2. correlation_id  -> un identificador para emparejar respuesta con pregunta,
                        porque por una misma cola de respuestas pueden llegar
                        varias, y no necesariamente en orden.
  3. content_type    -> "application/json", para que el converter de Jackson en
                        el lado Java sepa qué hacer con el cuerpo.

Al @RabbitListener de Java no hay que añadirle nada: cuando su método devuelve
un valor, Spring lo publica solo en la cola que indica reply_to.
"""

import asyncio
import json
import logging
import uuid

import aio_pika
from aio_pika.abc import AbstractIncomingMessage, AbstractQueue

from app.messaging import broker, topology

logger = logging.getLogger(__name__)

# Cuánto se espera una respuesta antes de rendirse. Equivale al
# spring.rabbitmq.template.reply-timeout de los servicios Java.
TIMEOUT_SEGUNDOS = 5

_cola_respuestas: AbstractQueue | None = None
_pendientes: dict[str, asyncio.Future] = {}


async def iniciar_cliente_rpc() -> bool:
    """Crea la cola de respuestas de este cliente. Devuelve False si no hay canal.

    La cola es exclusive=True: pertenece a esta conexión y RabbitMQ la borra al
    cerrarla. No hace falta darle nombre ni limpiarla a mano.
    """
    global _cola_respuestas

    channel = broker.get_channel()
    if channel is None:
        logger.warning("Sin canal de RabbitMQ; el cliente RPC no arranca")
        return False

    _cola_respuestas = await channel.declare_queue(exclusive=True)
    await _cola_respuestas.consume(_recibir_respuesta, no_ack=True)

    logger.info("Cliente RPC listo, cola de respuestas '%s'", _cola_respuestas.name)
    return True


async def _recibir_respuesta(mensaje: AbstractIncomingMessage) -> None:
    """Entrega la respuesta al await que la está esperando."""
    if mensaje.correlation_id is None:
        logger.warning("Respuesta RPC sin correlation_id, se descarta")
        return

    future = _pendientes.pop(mensaje.correlation_id, None)
    if future is None:
        # Suele ser una respuesta que llegó después de su propio timeout.
        logger.warning("Respuesta RPC sin petición asociada: %s", mensaje.correlation_id)
        return

    if not future.done():
        future.set_result(mensaje.body)


async def consultar_paciente(paciente_id: int) -> dict | None:
    """Pregunta a pacientes-service por un paciente y espera la respuesta.

    Devuelve el dict de la respuesta, o None si no hubo respuesta a tiempo (el
    otro servicio está caído o nadie atiende la cola).
    """
    if _cola_respuestas is None or not broker.esta_conectado():
        logger.warning("RPC no disponible: sin conexión a RabbitMQ")
        return None

    exchange = broker.get_exchange_rpc()
    if exchange is None:
        return None

    correlation_id = str(uuid.uuid4())
    future: asyncio.Future = asyncio.get_running_loop().create_future()
    _pendientes[correlation_id] = future

    try:
        await exchange.publish(
            aio_pika.Message(
                body=json.dumps({"pacienteId": paciente_id}).encode(),
                content_type="application/json",
                correlation_id=correlation_id,
                reply_to=_cola_respuestas.name,
                # Caducidad de la petición, igual al timeout de esta espera.
                # Sin ella, una pregunta enviada mientras pacientes-service está
                # caído se queda en la cola durable "pacientes.rpc" y se atiende
                # cuando el servicio vuelve, contestando a un cliente que ya se
                # rindió. Con expiration RabbitMQ descarta solo lo vencido.
                expiration=TIMEOUT_SEGUNDOS,
            ),
            routing_key=topology.RK_RPC_PACIENTES,
        )

        cuerpo = await asyncio.wait_for(future, timeout=TIMEOUT_SEGUNDOS)
        return json.loads(cuerpo.decode())

    except asyncio.TimeoutError:
        logger.warning("Timeout de %ss esperando el RPC del paciente %s",
                       TIMEOUT_SEGUNDOS, paciente_id)
        return None
    except Exception as e:
        logger.error("Fallo en el RPC del paciente %s: %s", paciente_id, e)
        return None
    finally:
        # Si se salió por timeout o error, la entrada sigue en el diccionario y
        # hay que quitarla: si no, _pendientes crece sin límite.
        _pendientes.pop(correlation_id, None)
