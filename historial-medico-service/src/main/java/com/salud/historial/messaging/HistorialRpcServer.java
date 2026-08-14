package com.salud.historial.messaging;

import com.salud.historial.config.RabbitMQConfig;
import com.salud.historial.dto.HistorialMedicoResponse;
import com.salud.historial.dto.rpc.ConsultaHistorialRequest;
import com.salud.historial.dto.rpc.ConsultaHistorialRespuesta;
import com.salud.historial.service.HistorialMedicoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Lado servidor del RPC: atiende la cola "historial.rpc".
 *
 * Cuando el método anotado DEVUELVE un valor, Spring AMQP lo publica en la cola
 * que indica la propiedad "reply-to" del mensaje entrante, con el mismo
 * "correlation-id". Ese ida y vuelta es todo el patrón request/reply.
 *
 * Su gemelo en pacientes-service lleva @Transactional(readOnly = true) y este
 * no, por el mismo motivo que PacienteEventoListener: MongoDB standalone no
 * tiene transacciones que abrir.
 */
@Component
@RequiredArgsConstructor
public class HistorialRpcServer {

    private static final Logger log = LoggerFactory.getLogger(HistorialRpcServer.class);

    private final HistorialMedicoService historialService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_RPC_HISTORIAL)
    public ConsultaHistorialRespuesta responder(ConsultaHistorialRequest request) {
        Long pacienteId = request.pacienteId();
        log.debug("Consulta RPC recibida del historial del paciente {}", pacienteId);

        if (pacienteId == null) {
            return new ConsultaHistorialRespuesta(null, List.of());
        }

        // Se leen los historiales directamente, sin validar que el paciente
        // exista: quien pregunta es pacientes-service, que ya sabe que existe
        // porque es el dueño del dato. Validarlo aquí provocaría un RPC de
        // vuelta hacia quien nos acaba de preguntar.
        List<HistorialMedicoResponse> historiales = historialService.listarPorPacienteSinValidar(pacienteId);

        return new ConsultaHistorialRespuesta(pacienteId, historiales);
    }
}
