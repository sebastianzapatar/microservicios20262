package com.salud.historial.service;

import com.salud.historial.dto.rpc.ConsultaPacienteRespuesta;
import com.salud.historial.messaging.PacienteRpcClient;
import com.salud.historial.model.PacienteReplica;
import com.salud.historial.repository.PacienteReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Decide si un paciente existe combinando los dos patrones de mensajería.
 *
 * Estrategia: PRIMERO la réplica local (que mantienen los eventos "paciente.*"),
 * y solo si no está, RPC a pacientes-service.
 *
 * El fallback no es decorativo: la réplica arranca vacía. Los pacientes creados
 * antes de que existiera la cola —o mientras el consumidor estaba caído— no
 * están en ella, y sin el RPC este servicio rechazaría historiales de pacientes
 * perfectamente válidos. Cuando el RPC confirma uno, se guarda en la réplica
 * para que la siguiente vez ya se resuelva en local.
 */
@Service
@RequiredArgsConstructor
public class PacienteValidador {

    private static final Logger log = LoggerFactory.getLogger(PacienteValidador.class);

    private final PacienteReplicaRepository replicaRepository;
    private final PacienteRpcClient pacienteRpcClient;

    /**
     * @throws RuntimeException si el paciente no está en la réplica y el RPC no
     *         obtuvo respuesta (se traduce a 503, no a 404).
     */
    public boolean existe(Long pacienteId) {
        if (pacienteId == null) {
            return false;
        }

        if (replicaRepository.existsById(pacienteId)) {
            log.debug("Paciente {} validado contra la réplica local, sin salir a la red", pacienteId);
            return true;
        }

        log.debug("Paciente {} no está en la réplica local; se pregunta por RPC", pacienteId);
        ConsultaPacienteRespuesta respuesta = pacienteRpcClient.consultar(pacienteId);

        if (respuesta.existe()) {
            rellenarReplica(respuesta);
            return true;
        }

        return false;
    }

    /** Guarda en la réplica un paciente que el RPC acaba de confirmar. */
    private void rellenarReplica(ConsultaPacienteRespuesta respuesta) {
        replicaRepository.save(PacienteReplica.builder()
                .id(respuesta.pacienteId())
                .nombre(respuesta.nombre())
                .apellido(respuesta.apellido())
                .email(respuesta.email())
                .tipoDocumento(respuesta.tipoDocumento())
                .numeroDocumento(respuesta.numeroDocumento())
                .fechaSincronizacion(LocalDateTime.now())
                .build());

        log.debug("Paciente {} añadido a la réplica local tras confirmarlo por RPC", respuesta.pacienteId());
    }
}
