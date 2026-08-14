package com.salud.pacientes.dto.evento;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Evento que este servicio PUBLICA cuando un paciente cambia.
 *
 * Viaja como JSON por el exchange "salud.events". El campo "tipo" repite la
 * routing key dentro del cuerpo para que el mensaje se explique solo: un
 * consumidor puede decidir qué hacer sin leer las cabeceras AMQP, lo que
 * simplifica el consumidor de Python.
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
