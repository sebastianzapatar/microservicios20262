package com.salud.historial.dto.rpc;

/**
 * Respuesta que llega desde pacientes-service por la cola "pacientes.rpc".
 *
 * El "no existe" viaja como un booleano y no como una excepción: propagar
 * excepciones a través de una cola obliga a serializar el error, y para este
 * caso un campo es más simple.
 */
public record ConsultaPacienteRespuesta(
        boolean existe,
        Long pacienteId,
        String nombre,
        String apellido,
        String email,
        String tipoDocumento,
        String numeroDocumento
) {
}
