package com.salud.pacientes.messaging;

import com.salud.pacientes.config.RabbitMQConfig;
import com.salud.pacientes.dto.rpc.ConsultaPacienteRequest;
import com.salud.pacientes.dto.rpc.ConsultaPacienteRespuesta;
import com.salud.pacientes.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lado servidor del RPC: atiende la cola "pacientes.rpc".
 *
 * Cuando el método anotado DEVUELVE un valor, Spring AMQP lo publica
 * automáticamente en la cola indicada por la propiedad "reply-to" del mensaje
 * entrante, con el mismo "correlation-id". Ese ida y vuelta es todo el patrón
 * request/reply: no hay que gestionar la cola de respuesta a mano.
 */
@Component
@RequiredArgsConstructor
public class PacienteRpcServer {

    private static final Logger log = LoggerFactory.getLogger(PacienteRpcServer.class);

    private final PacienteRepository pacienteRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_RPC_PACIENTES)
    @Transactional(readOnly = true)
    public ConsultaPacienteRespuesta responder(ConsultaPacienteRequest request) {
        Long pacienteId = request.pacienteId();
        log.debug("Consulta RPC recibida para el paciente {}", pacienteId);

        if (pacienteId == null) {
            return ConsultaPacienteRespuesta.noEncontrado(null);
        }

        return pacienteRepository.findById(pacienteId)
                .map(paciente -> new ConsultaPacienteRespuesta(
                        true,
                        paciente.getId(),
                        paciente.getNombre(),
                        paciente.getApellido(),
                        paciente.getEmail(),
                        paciente.getTipoDocumento(),
                        paciente.getNumeroDocumento()))
                .orElseGet(() -> ConsultaPacienteRespuesta.noEncontrado(pacienteId));
    }
}
