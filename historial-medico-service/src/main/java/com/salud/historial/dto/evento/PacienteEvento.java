package com.salud.historial.dto.evento;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Evento que este servicio CONSUME desde pacientes-service.
 *
 * Es un "lector tolerante": si el emisor añade campos nuevos, este record los
 * ignora en lugar de romperse (FAIL_ON_UNKNOWN_PROPERTIES está desactivado en
 * {@link com.salud.historial.config.RabbitMQConfig}).
 */
public record PacienteEvento(
        String tipo,
        Long pacienteId,
        String nombre,
        String apellido,
        String email,
        String telefono,
        LocalDate fechaNacimiento,
        String direccion,
        String tipoDocumento,
        String numeroDocumento,
        LocalDateTime ocurridoEn
) {
}
