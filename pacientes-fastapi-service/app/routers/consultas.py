from typing import List
from fastapi import APIRouter, Depends, HTTPException
from app.core.security import get_current_user_id
from app.db.mongodb import get_mongo_db
from app.messaging import topology
from app.models.history import HistorialMedicoResponse
from app.models.notification import NotificacionResponse, PacienteReplicaResponse

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
    """Lee lo que dejó el consumidor de Kafka.

    Estas notificaciones no las genera este endpoint: las fue creando
    app.messaging.consumer a medida que llegaban los eventos "historial.*" por
    el topic "salud.historiales". Aquí solo se leen. Es lo que permite que este
    servicio "sepa" que hay un registro médico nuevo sin haberle preguntado nada
    a nadie.
    """
    db = get_mongo_db()
    if db is None:
        raise HTTPException(status_code=500, detail="Database no inicializada")

    cursor = db[topology.COLECCION_NOTIFICACIONES].find({"pacienteId": user_id})
    notificaciones = await cursor.to_list(length=100)

    for n in notificaciones:
        n["_id"] = str(n["_id"])

    return [NotificacionResponse(**n) for n in notificaciones]


@router.get("/mi-paciente", response_model=PacienteReplicaResponse)
async def consultar_mis_datos_de_paciente(user_id: int = Depends(get_current_user_id)):
    """Lee la réplica local de pacientes, construida con los eventos de Kafka.

    Compara este endpoint con su equivalente en la rama de RabbitMQ. Allí hacía
    un RPC a pacientes-service y podía devolver 503 si nadie contestaba a
    tiempo. Aquí lee una colección propia: la respuesta no depende de que el
    otro microservicio esté vivo, y no hay ningún timeout que se pueda agotar.

    El campo "fechaSincronizacion" deja ver hasta qué momento llegó la réplica,
    que es el precio que se paga: consistencia eventual en vez de dato fresco.
    """
    db = get_mongo_db()
    if db is None:
        raise HTTPException(status_code=500, detail="Database no inicializada")

    doc = await db[topology.COLECCION_PACIENTES].find_one({"_id": user_id})

    if doc is None:
        # Aquí un 404 es honesto: si el paciente existiera, el evento ya habría
        # llegado (el consumidor lee el topic entero desde el offset 0).
        raise HTTPException(
            status_code=404,
            detail=f"No existe un paciente con ID {user_id} en la réplica local",
        )

    return PacienteReplicaResponse(
        pacienteId=doc["_id"],
        nombre=doc.get("nombre"),
        apellido=doc.get("apellido"),
        email=doc.get("email"),
        telefono=doc.get("telefono"),
        direccion=doc.get("direccion"),
        tipoDocumento=doc.get("tipoDocumento"),
        numeroDocumento=doc.get("numeroDocumento"),
        fechaSincronizacion=doc.get("fechaSincronizacion"),
    )
