package com.salud.pacientes.dto;

import java.time.LocalDateTime;

public record HistorialMedicoResponse(
        String id,
        Long pacienteId,
        String diagnostico,
        String tratamiento,
        String medico,
        LocalDateTime fechaConsulta,
        String notas,
        String tipoConsulta
) {
}
