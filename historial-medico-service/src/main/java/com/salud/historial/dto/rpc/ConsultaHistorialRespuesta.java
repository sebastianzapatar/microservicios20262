package com.salud.historial.dto.rpc;

import com.salud.historial.dto.HistorialMedicoResponse;

import java.util.List;

/**
 * Respuesta que este servicio devuelve por la cola "historial.rpc".
 *
 * Se reutiliza el DTO que ya usa el API REST. Lleva dos campos más
 * (fechaCreacion, fechaActualizacion) que los que declara el record equivalente
 * en pacientes-service; los sobrantes se descartan allí al deserializar.
 */
public record ConsultaHistorialRespuesta(
        Long pacienteId,
        List<HistorialMedicoResponse> historiales
) {
}
