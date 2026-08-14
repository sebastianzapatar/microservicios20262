"""Conexión a RabbitMQ y declaración de la topología.

Se usa connect_robust: aio-pika reconecta solo si el broker se cae o se
reinicia, y vuelve a declarar exchanges, colas y consumidores. Con connect() a
secas habría que gestionar la reconexión a mano.
"""

import asyncio
import logging

import aio_pika
from aio_pika.abc import AbstractRobustConnection, AbstractRobustChannel

from app.core.config import settings
from app.messaging import topology

logger = logging.getLogger(__name__)

_connection: AbstractRobustConnection | None = None
_channel: AbstractRobustChannel | None = None
_exchange_rpc: aio_pika.abc.AbstractExchange | None = None


async def conectar(intentos: int = 15, espera: int = 4) -> bool:
    """Conecta y declara la topología, reintentando mientras el broker arranca.

    Docker Compose puede levantar este contenedor antes de que RabbitMQ acepte
    conexiones, igual que pasa con MySQL. Devuelve False si no se logró
    conectar: el servicio arranca igualmente en modo degradado (sus endpoints
    REST siguen funcionando) en lugar de quedarse sin arrancar.
    """
    global _connection, _channel, _exchange_rpc

    for intento in range(1, intentos + 1):
        try:
            _connection = await aio_pika.connect_robust(settings.RABBITMQ_URL)
            _channel = await _connection.channel()

            # prefetch=10: como máximo 10 mensajes sin confirmar a la vez. Sin
            # esto RabbitMQ entregaría toda la cola de golpe y el consumidor
            # perdería el efecto amortiguador de la cola.
            await _channel.set_qos(prefetch_count=10)

            _exchange_rpc = await declarar_topologia(_channel)

            logger.info("Conectado a RabbitMQ en %s", settings.RABBITMQ_HOST)
            return True
        except Exception as e:
            # Puede haberse abierto la conexión y haber fallado la declaración
            # de la topología (un PRECONDITION_FAILED, por ejemplo). Si no se
            # cierra aquí, cada reintento deja una conexión huérfana abierta
            # contra el broker.
            await _descartar_conexion_fallida()

            logger.warning("RabbitMQ no disponible (intento %d/%d): %s", intento, intentos, e)
            if intento == intentos:
                logger.error("Se agotaron los intentos de conexión a RabbitMQ; "
                             "el servicio arranca sin mensajería")
                return False
            await asyncio.sleep(espera)

    return False


async def _descartar_conexion_fallida() -> None:
    """Cierra y olvida una conexión a medio inicializar, sin propagar errores."""
    global _connection, _channel, _exchange_rpc

    if _connection is not None:
        try:
            await _connection.close()
        except Exception:
            logger.debug("No se pudo cerrar la conexión fallida; se descarta igual")

    _connection = None
    _channel = None
    _exchange_rpc = None


async def declarar_topologia(channel: AbstractRobustChannel) -> aio_pika.abc.AbstractExchange:
    """Declara exchanges, la cola propia y sus bindings. Devuelve el exchange RPC.

    Declarar es idempotente: si los servicios Java ya crearon estos recursos con
    los mismos argumentos, esta llamada no hace nada. Lo que no se puede es
    declararlos con argumentos distintos.
    """
    exchange_eventos = await channel.declare_exchange(
        topology.EXCHANGE_EVENTOS, aio_pika.ExchangeType.TOPIC, durable=True
    )
    exchange_rpc = await channel.declare_exchange(
        topology.EXCHANGE_RPC, aio_pika.ExchangeType.DIRECT, durable=True
    )
    exchange_dlx = await channel.declare_exchange(
        topology.EXCHANGE_DLX, aio_pika.ExchangeType.TOPIC, durable=True
    )

    # La cola de descarte, con los MISMOS argumentos que declaran los servicios
    # Java. Declararla aquí evita que los mensajes rechazados por este servicio
    # se pierdan si FastAPI arranca antes que ellos (ver topology.QUEUE_DLQ).
    dlq = await channel.declare_queue(
        topology.QUEUE_DLQ,
        durable=True,
        arguments={"x-message-ttl": topology.TTL_DLQ_MS},
    )
    await dlq.bind(exchange_dlx, routing_key=topology.PATRON_DLQ)

    cola = await channel.declare_queue(
        topology.QUEUE_EVENTOS,
        durable=True,
        # Mismo argumento que QueueBuilder.deadLetterExchange() en Java: los
        # mensajes rechazados salen por el DLX en lugar de perderse.
        arguments={"x-dead-letter-exchange": topology.EXCHANGE_DLX},
    )

    # Una cola, dos bindings: recibe tanto los eventos de pacientes como los de
    # historiales por el mismo canal.
    await cola.bind(exchange_eventos, routing_key=topology.PATRON_EVENTOS_PACIENTES)
    await cola.bind(exchange_eventos, routing_key=topology.PATRON_EVENTOS_HISTORIAL)

    logger.info("Topología declarada: cola '%s' suscrita a '%s' y '%s'; descartes a '%s'",
                topology.QUEUE_EVENTOS,
                topology.PATRON_EVENTOS_PACIENTES,
                topology.PATRON_EVENTOS_HISTORIAL,
                topology.QUEUE_DLQ)

    return exchange_rpc


def get_channel() -> AbstractRobustChannel | None:
    return _channel


def get_exchange_rpc() -> aio_pika.abc.AbstractExchange | None:
    return _exchange_rpc


def esta_conectado() -> bool:
    return _connection is not None and not _connection.is_closed


async def cerrar() -> None:
    """Cierra la conexión en el shutdown para no dejar consumidores colgando."""
    global _connection, _channel, _exchange_rpc

    if _connection is not None and not _connection.is_closed:
        await _connection.close()
        logger.info("Conexión a RabbitMQ cerrada")

    _connection = None
    _channel = None
    _exchange_rpc = None
