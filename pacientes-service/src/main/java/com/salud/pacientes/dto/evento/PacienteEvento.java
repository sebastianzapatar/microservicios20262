package com.salud.pacientes.dto.evento;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Evento que este servicio PUBLICA en el topic "salud.pacientes" cuando un
 * paciente cambia.
 *
 * Viaja como JSON. El campo "tipo" repite el nombre del evento dentro del
 * cuerpo para que el mensaje se explique solo: un consumidor puede decidir qué
 * hacer sin leer cabeceras, lo que simplifica mucho el consumidor de Python.
 *
 * La CLAVE del mensaje (que no va aquí dentro, va aparte en Kafka) es el
 * pacienteId. Eso garantiza dos cosas: que todos los eventos de un paciente
 * caen en la misma partición y se procesan en orden, y que la compactación
 * conserva el último estado de cada paciente para siempre.
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
