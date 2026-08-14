package com.salud.pacientes.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PacienteResponse(
        Long id,
        String nombre,
        String apellido,
        String email,
        String telefono,
        LocalDate fechaNacimiento,
        String direccion,
        String tipoDocumento,
        String numeroDocumento,
        LocalDateTime fechaRegistro,
        LocalDateTime fechaActualizacion
) {
}
