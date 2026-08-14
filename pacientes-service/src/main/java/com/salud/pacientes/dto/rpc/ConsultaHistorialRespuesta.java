package com.salud.pacientes.dto.rpc;

import com.salud.pacientes.dto.HistorialMedicoResponse;

import java.util.List;

/**
 * Respuesta que llega desde historial-medico-service por la cola "historial.rpc".
 *
 * El emisor manda cada historial con más campos de los que declara
 * {@link HistorialMedicoResponse} (fechaCreacion, fechaActualizacion). Los
 * sobrantes se descartan al deserializar; el contrato es "los campos que
 * necesito", no "todos los que existen".
 */
public record ConsultaHistorialRespuesta(
        Long pacienteId,
        List<HistorialMedicoResponse> historiales
) {
}
