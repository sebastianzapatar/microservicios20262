package com.salud.historial.dto.evento;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Evento que este servicio CONSUME del topic "salud.pacientes", publicado por
 * pacientes-service.
 *
 * Es una copia del record que declara el emisor, no una clase compartida: cada
 * microservicio es dueño de su propia versión del contrato. Se acepta la
 * duplicación a cambio de poder desplegar los servicios por separado.
 *
 * En los eventos de borrado NO llega ningún cuerpo: Kafka recibe un mensaje con
 * valor nulo (tombstone) y la única información es la clave. Ver
 * {@link com.salud.historial.messaging.PacienteEventoListener}.
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
