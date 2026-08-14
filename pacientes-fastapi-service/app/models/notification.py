from typing import Optional

from pydantic import BaseModel, Field


class NotificacionResponse(BaseModel):
    """Aviso generado a partir de un evento "historial.*" recibido por RabbitMQ."""

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


class PacienteRpcResponse(BaseModel):
    """Respuesta de pacientes-service a una consulta RPC por cola."""

    existe: bool
    pacienteId: Optional[int] = None
    nombre: Optional[str] = None
    apellido: Optional[str] = None
    email: Optional[str] = None
    tipoDocumento: Optional[str] = None
    numeroDocumento: Optional[str] = None
