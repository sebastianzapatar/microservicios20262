package com.salud.historial.dto.evento;

import java.time.LocalDateTime;

/**
 * Evento que este servicio PUBLICA cuando un historial médico cambia.
 *
 * El campo "tipo" repite la routing key dentro del cuerpo para que el mensaje se
 * explique solo, sin obligar al consumidor a leer las cabeceras AMQP.
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
