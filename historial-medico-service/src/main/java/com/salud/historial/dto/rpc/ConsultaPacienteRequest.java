package com.salud.historial.dto.rpc;

/** Pregunta que este servicio envía a la cola "pacientes.rpc". */
public record ConsultaPacienteRequest(Long pacienteId) {
}
