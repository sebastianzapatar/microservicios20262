from typing import List
from fastapi import APIRouter, Depends, HTTPException
from app.core.security import get_current_user_id
from app.db.mongodb import get_mongo_db
from app.messaging import topology
from app.messaging.rpc_client import consultar_paciente
from app.models.history import HistorialMedicoResponse
from app.models.notification import NotificacionResponse, PacienteRpcResponse

router = APIRouter(prefix="/api/consultas", tags=["Consultas Médicas"])

@router.get("/historial", response_model=List[HistorialMedicoResponse])
async def consultar_mi_historial(user_id: int = Depends(get_current_user_id)):
    """
    Obtiene todos los registros del historial médico para el usuario autenticado.
    El user_id extraído del JWT actúa como el pacienteId.
    """
    db = get_mongo_db()
    if db is None:
        raise HTTPException(status_code=500, detail="Database no inicializada")

    collection = db["historiales_medicos"]
    
    # Buscar en la colección usando el ID del usuario
    cursor = collection.find({"pacienteId": user_id})
    historiales = await cursor.to_list(length=100)
    
    if not historiales:
        return []
    
    # Procesar la data (ObjectID -> str)
    resultado = []
    for h in historiales:
        h["_id"] = str(h["_id"])
        if "fechaConsulta" in h and h["fechaConsulta"]:
            h["fechaConsulta"] = h["fechaConsulta"].isoformat() if hasattr(h["fechaConsulta"], 'isoformat') else str(h["fechaConsulta"])
        if "fechaCreacion" in h and h["fechaCreacion"]:
            h["fechaCreacion"] = h["fechaCreacion"].isoformat() if hasattr(h["fechaCreacion"], 'isoformat') else str(h["fechaCreacion"])
        if "fechaActualizacion" in h and h["fechaActualizacion"]:
            h["fechaActualizacion"] = h["fechaActualizacion"].isoformat() if hasattr(h["fechaActualizacion"], 'isoformat') else str(h["fechaActualizacion"])
        resultado.append(HistorialMedicoResponse(**h))

    return resultado


@router.get("/notificaciones", response_model=List[NotificacionResponse])
async def consultar_mis_notificaciones(user_id: int = Depends(get_current_user_id)):
    """PATRÓN 1 - Eventos: lee lo que dejó el consumidor de la cola.

    Estas notificaciones no las genera este endpoint: las fue creando
    app.messaging.consumer a medida que llegaban los eventos "historial.*" por
    RabbitMQ. Aquí solo se leen. Es lo que permite que este servicio "sepa" que
    hay un registro médico nuevo sin haberle preguntado nada a nadie.
    """
    db = get_mongo_db()
    if db is None:
        raise HTTPException(status_code=500, detail="Database no inicializada")

    cursor = db[topology.COLECCION_NOTIFICACIONES].find({"pacienteId": user_id})
    notificaciones = await cursor.to_list(length=100)

    for n in notificaciones:
        n["_id"] = str(n["_id"])

    return [NotificacionResponse(**n) for n in notificaciones]


@router.get("/mi-paciente", response_model=PacienteRpcResponse)
async def consultar_mis_datos_de_paciente(user_id: int = Depends(get_current_user_id)):
    """PATRÓN 2 - RPC sobre RabbitMQ: pregunta y espera respuesta.

    Publica una petición en la cola "pacientes.rpc" que atiende pacientes-service
    (Java) y espera la respuesta. Sin URLs, sin Eureka y sin HTTP: Python y Java
    se entienden porque comparten el mismo contrato JSON y el mismo protocolo
    request/reply de AMQP.

    Devuelve 503 si nadie contestó a tiempo, para no confundir "el otro servicio
    está caído" con "el paciente no existe".
    """
    respuesta = await consultar_paciente(user_id)

    if respuesta is None:
        raise HTTPException(
            status_code=503,
            detail="Servicio de Pacientes no disponible: sin respuesta en la cola RPC",
        )

    if not respuesta.get("existe"):
        raise HTTPException(
            status_code=404,
            detail=f"No existe un paciente con ID {user_id}",
        )

    return PacienteRpcResponse(**respuesta)
