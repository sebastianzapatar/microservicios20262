package com.salud.pacientes.messaging;

import com.salud.pacientes.config.RabbitMQConfig;
import com.salud.pacientes.dto.HistorialMedicoResponse;
import com.salud.pacientes.dto.rpc.ConsultaHistorialRequest;
import com.salud.pacientes.dto.rpc.ConsultaHistorialRespuesta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Lado cliente del RPC: pregunta a historial-medico-service por la cola
 * "historial.rpc" y espera la respuesta.
 *
 * Sustituye al antiguo HistorialMedicoClient, que resolvía la URL del otro
 * servicio con Eureka y hacía un GET. Diferencia importante: aquí no se conoce
 * ni la URL ni el número de instancias del otro servicio. Se publica en el
 * exchange y RabbitMQ reparte entre los consumidores de la cola, que además
 * hace de balanceador y de buffer si el consumidor va lento.
 *
 * Sigue siendo una llamada BLOQUEANTE: se espera la respuesta hasta el
 * spring.rabbitmq.template.reply-timeout configurado en application.yml.
 */
@Component
public class HistorialRpcClient {

    private static final Logger log = LoggerFactory.getLogger(HistorialRpcClient.class);

    private final RabbitTemplate rabbitTemplate;

    /**
     * Marca la petición para que el broker la descarte al vencer.
     *
     * Sin esto, una pregunta enviada mientras el otro servicio está caído se
     * queda esperando en la cola durable "historial.rpc". Cuando el servicio
     * vuelve —minutos después— la atiende y contesta a un cliente que hace rato
     * se rindió: trabajo inútil y un ERROR "Reply received after timeout" en el
     * log que parece un fallo sin serlo.
     *
     * Con expiration, RabbitMQ tira solo las peticiones vencidas. Se usa el
     * MISMO valor que reply-timeout: si el cliente ya no espera, nadie trabaja.
     */
    private final MessagePostProcessor caducidad;

    public HistorialRpcClient(RabbitTemplate rabbitTemplate,
                              @Value("${spring.rabbitmq.template.reply-timeout}") Duration replyTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        // La propiedad "expiration" de AMQP viaja como texto, en milisegundos.
        String ttlMs = String.valueOf(replyTimeout.toMillis());
        this.caducidad = mensaje -> {
            mensaje.getMessageProperties().setExpiration(ttlMs);
            return mensaje;
        };
    }

    public List<HistorialMedicoResponse> obtenerHistorialPorPaciente(Long pacienteId) {
        try {
            ConsultaHistorialRespuesta respuesta = rabbitTemplate.convertSendAndReceiveAsType(
                    RabbitMQConfig.EXCHANGE_RPC,
                    RabbitMQConfig.RK_RPC_HISTORIAL,
                    new ConsultaHistorialRequest(pacienteId),
                    caducidad,
                    new ParameterizedTypeReference<>() {
                    });

            if (respuesta == null || respuesta.historiales() == null) {
                // convertSendAndReceive devuelve null si se agota el timeout,
                // es decir si nadie atendió la cola a tiempo.
                log.warn("Sin respuesta del RPC de historial para el paciente {} (timeout o cola sin consumidores)",
                        pacienteId);
                return Collections.emptyList();
            }

            return respuesta.historiales();
        } catch (Exception e) {
            // Degradación elegante: el paciente se devuelve igual, pero sin
            // historial. Se registra el motivo porque un catch silencioso
            // convierte un error en un "no hay datos" indistinguible.
            log.error("Fallo en el RPC de historial para el paciente {}: {}", pacienteId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
