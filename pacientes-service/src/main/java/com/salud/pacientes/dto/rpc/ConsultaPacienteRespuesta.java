package com.salud.pacientes.dto.rpc;

/**
 * Respuesta que este servicio devuelve por la cola "pacientes.rpc".
 *
 * Lleva un booleano "existe" en lugar de lanzar una excepción cuando el
 * paciente no está: propagar excepciones a través de una cola obliga a
 * serializar el error, y para el caso "no lo encontré" un campo es más simple
 * y más fácil de consumir desde otro lenguaje.
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
    public static ConsultaPacienteRespuesta noEncontrado(Long pacienteId) {
        return new ConsultaPacienteRespuesta(false, pacienteId, null, null, null, null, null);
    }
}
