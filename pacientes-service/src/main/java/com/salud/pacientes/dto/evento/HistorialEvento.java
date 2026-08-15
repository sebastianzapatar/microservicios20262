package com.salud.pacientes.dto.evento;

import java.time.LocalDateTime;

/**
 * Evento que este servicio CONSUME del topic "salud.historiales", publicado por
 * historial-medico-service.
 *
 * Es una copia del record que declara el emisor, no una clase compartida: cada
 * microservicio es dueño de su propia versión del contrato. Se acepta la
 * duplicación a cambio de poder desplegar los servicios por separado.
 *
 * En los eventos de borrado NO llega ningún cuerpo: Kafka recibe un mensaje con
 * valor nulo (tombstone) y la única información es la clave. Ver
 * {@link com.salud.pacientes.messaging.HistorialEventoListener}.
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
