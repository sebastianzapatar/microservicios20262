package com.salud.historial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record HistorialMedicoRequest(
        @NotNull(message = "El ID del paciente es obligatorio")
        Long pacienteId,

        @NotBlank(message = "El diagnóstico es obligatorio")
        String diagnostico,

        @NotBlank(message = "El tratamiento es obligatorio")
        String tratamiento,

        @NotBlank(message = "El nombre del médico es obligatorio")
        String medico,

        LocalDateTime fechaConsulta,

        String notas,

        @NotBlank(message = "El tipo de consulta es obligatorio")
        String tipoConsulta
) {
}
