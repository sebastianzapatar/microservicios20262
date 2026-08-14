package com.salud.pacientes.dto.rpc;

/** Pregunta que este servicio envía a la cola "historial.rpc". */
public record ConsultaHistorialRequest(Long pacienteId) {
}
