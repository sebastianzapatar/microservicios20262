package com.salud.historial.messaging;

import com.salud.historial.config.RabbitMQConfig;
import com.salud.historial.dto.rpc.ConsultaPacienteRequest;
import com.salud.historial.dto.rpc.ConsultaPacienteRespuesta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Lado cliente del RPC: pregunta a pacientes-service por la cola "pacientes.rpc"
 * y espera la respuesta.
 *
 * Sustituye al antiguo PacienteClient, que resolvía la URL con Eureka y hacía un
 * GET. Aquí no se conoce la URL ni el número de instancias del otro servicio:
 * se publica en el exchange y RabbitMQ reparte entre los consumidores de la cola.
 *
 * Sigue siendo una llamada BLOQUEANTE, limitada por
 * spring.rabbitmq.template.reply-timeout.
 */
@Component
public class PacienteRpcClient {

    private static final Logger log = LoggerFactory.getLogger(PacienteRpcClient.class);

    private final RabbitTemplate rabbitTemplate;

    /**
     * Marca la petición para que el broker la descarte al vencer.
     *
     * Sin esto, una pregunta enviada mientras pacientes-service está caído se
     * queda esperando en la cola durable "pacientes.rpc". Cuando el servicio
     * vuelve la atiende y contesta a un cliente que ya se rindió: trabajo inútil
     * y un ERROR "Reply received after timeout" que parece un fallo sin serlo.
     *
     * Se usa el MISMO valor que reply-timeout: si el cliente ya no espera la
     * respuesta, no tiene sentido que nadie calcule esa respuesta.
     */
    private final MessagePostProcessor caducidad;

    public PacienteRpcClient(RabbitTemplate rabbitTemplate,
                             @Value("${spring.rabbitmq.template.reply-timeout}") Duration replyTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        // La propiedad "expiration" de AMQP viaja como texto, en milisegundos.
        String ttlMs = String.valueOf(replyTimeout.toMillis());
        this.caducidad = mensaje -> {
            mensaje.getMessageProperties().setExpiration(ttlMs);
            return mensaje;
        };
    }

    /**
     * @return la respuesta del otro servicio, nunca null.
     * @throws RuntimeException si nadie contestó a tiempo. Se distingue a
     *         propósito de "el paciente no existe": tratar un timeout como un
     *         "no existe" haría rechazar historiales de pacientes válidos.
     *         El mensaje incluye "no disponible" para que GlobalExceptionHandler
     *         lo traduzca a un 503 y no a un 404.
     */
    public ConsultaPacienteRespuesta consultar(Long pacienteId) {
        ConsultaPacienteRespuesta respuesta;
        try {
            respuesta = rabbitTemplate.convertSendAndReceiveAsType(
                    RabbitMQConfig.EXCHANGE_RPC,
                    RabbitMQConfig.RK_RPC_PACIENTES,
                    new ConsultaPacienteRequest(pacienteId),
                    caducidad,
                    new ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            log.error("Fallo en el RPC de pacientes para el paciente {}: {}", pacienteId, e.getMessage(), e);
            throw new RuntimeException("Servicio de Pacientes no disponible: " + e.getMessage(), e);
        }

        if (respuesta == null) {
            // convertSendAndReceive devuelve null al agotarse el timeout, es
            // decir cuando nadie está atendiendo la cola "pacientes.rpc".
            log.warn("Sin respuesta del RPC de pacientes para el paciente {} (timeout)", pacienteId);
            throw new RuntimeException("Servicio de Pacientes no disponible: sin respuesta en la cola RPC");
        }

        return respuesta;
    }
}
