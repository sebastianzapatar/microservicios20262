package com.salud.pacientes.dto.rpc;

/** Pregunta que llega por la cola "pacientes.rpc". */
public record ConsultaPacienteRequest(Long pacienteId) {
}
