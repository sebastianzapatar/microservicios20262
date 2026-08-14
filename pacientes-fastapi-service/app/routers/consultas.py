from typing import List
from fastapi import APIRouter, Depends, HTTPException
from app.core.security import get_current_user_id
from app.db.mongodb import get_mongo_db
from app.models.history import HistorialMedicoResponse

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
