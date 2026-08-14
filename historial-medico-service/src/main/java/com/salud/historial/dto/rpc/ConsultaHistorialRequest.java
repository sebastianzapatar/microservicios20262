package com.salud.historial.dto.rpc;

/** Pregunta que llega por la cola "historial.rpc". */
public record ConsultaHistorialRequest(Long pacienteId) {
}
