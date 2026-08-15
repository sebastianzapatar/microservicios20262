from typing import Optional

from pydantic import BaseModel, Field


class NotificacionResponse(BaseModel):
    """Aviso generado a partir de un evento "historial.*" recibido por Kafka."""

    id: str = Field(alias="_id")
    pacienteId: int
    mensaje: str
    diagnostico: Optional[str] = None
    medico: Optional[str] = None
    tipoConsulta: Optional[str] = None
    fechaConsulta: Optional[str] = None
    # Cuándo ocurrió el hecho en el servicio que lo publicó, no cuándo se leyó aquí.
    ocurridoEn: Optional[str] = None
    leida: bool = False


class PacienteReplicaResponse(BaseModel):
    """Datos del paciente leídos de la réplica local que construyen los eventos.

    En la rama de RabbitMQ este mismo dato se obtenía preguntando por RPC a
    pacientes-service y podía fallar con un 503 si nadie contestaba. Aquí sale
    de una colección propia, así que la respuesta no depende de que el otro
    servicio esté vivo.
    """

    pacienteId: int
    nombre: Optional[str] = None
    apellido: Optional[str] = None
    email: Optional[str] = None
    telefono: Optional[str] = None
    direccion: Optional[str] = None
    tipoDocumento: Optional[str] = None
    numeroDocumento: Optional[str] = None
    # Hasta qué momento llegó la réplica: hace visible la consistencia eventual.
    fechaSincronizacion: Optional[str] = None
