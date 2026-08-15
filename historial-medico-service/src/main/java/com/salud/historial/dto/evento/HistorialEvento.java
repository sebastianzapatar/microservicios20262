package com.salud.historial.dto.evento;

import java.time.LocalDateTime;

/**
 * Evento que este servicio PUBLICA en el topic "salud.historiales" cuando un
 * historial médico cambia.
 *
 * El campo "tipo" repite el nombre del evento dentro del cuerpo para que el
 * mensaje se explique solo, sin obligar al consumidor a mirar cabeceras.
 *
 * La CLAVE del mensaje (aparte, en Kafka) es el historialId: así todos los
 * eventos de un mismo historial caen en la misma partición y se procesan en
 * orden, y la compactación conserva el estado final de cada historial.
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
