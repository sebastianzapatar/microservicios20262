from typing import Optional
from pydantic import BaseModel, Field

class HistorialMedicoResponse(BaseModel):
    id: str = Field(alias="_id")
    pacienteId: int
    diagnostico: str
    tratamiento: str
    medico: str
    tipoConsulta: str
    notas: Optional[str] = None
    fechaConsulta: Optional[str] = None
    fechaCreacion: Optional[str] = None
    fechaActualizacion: Optional[str] = None
