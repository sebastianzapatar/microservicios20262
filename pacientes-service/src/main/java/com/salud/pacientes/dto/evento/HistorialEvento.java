package com.salud.pacientes.dto.evento;

import java.time.LocalDateTime;

/**
 * Evento que este servicio CONSUME desde historial-medico-service.
 *
 * Es un "lector tolerante": si el emisor añade campos nuevos, este record los
 * ignora en lugar de romperse (FAIL_ON_UNKNOWN_PROPERTIES está desactivado en
 * {@link com.salud.pacientes.config.RabbitMQConfig}).
 */
public record HistorialEvento(
        String tipo,
        String historialId,
        Long pacienteId,
        String diagnostico,
        String tratamiento,
        String medico,
        LocalDateTime fechaConsulta,
        String notas,
        String tipoConsulta,
        LocalDateTime ocurridoEn
) {
}
