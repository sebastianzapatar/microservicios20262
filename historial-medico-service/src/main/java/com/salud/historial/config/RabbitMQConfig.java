package com.salud.historial.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Topología de RabbitMQ vista desde historial-medico-service. Es el espejo de
 * la de pacientes-service: los exchanges y la DLQ son los mismos, y lo que
 * cambia son las colas propias y las routing keys.
 *
 * 1) EVENTOS sobre "salud.events": este servicio PUBLICA
 *    "historial.creado/actualizado/eliminado" y CONSUME "paciente.*" para
 *    mantener una réplica local de pacientes (colección pacientes_replica) y
 *    poder validar sin preguntarle a nadie.
 *
 * 2) RPC sobre "salud.rpc": ATIENDE la cola "historial.rpc" (le piden el
 *    historial de un paciente) y PREGUNTA a "pacientes.rpc" cuando su réplica
 *    todavía no conoce a un paciente.
 *
 * Las declaraciones de los recursos compartidos deben coincidir EXACTAMENTE con
 * las del otro servicio (mismo durable/autoDelete) o RabbitMQ cierra el canal
 * con PRECONDITION_FAILED.
 */
@Configuration
public class RabbitMQConfig {

    // ---------- Exchanges (compartidos) ----------
    public static final String EXCHANGE_EVENTOS = "salud.events";
    public static final String EXCHANGE_RPC = "salud.rpc";
    public static final String EXCHANGE_DLX = "salud.dlx";

    // ---------- Colas ----------
    /** Cola de descarte compartida. */
    public static final String QUEUE_DLQ = "salud.dlq";

    /** Cola propia: aquí caen los eventos publicados por pacientes-service. */
    public static final String QUEUE_EVENTOS_PACIENTES = "historial.eventos.pacientes";

    /** Cola propia: aquí llegan las preguntas RPC dirigidas a este servicio. */
    public static final String QUEUE_RPC_HISTORIAL = "historial.rpc";

    // ---------- Routing keys ----------
    public static final String RK_HISTORIAL_CREADO = "historial.creado";
    public static final String RK_HISTORIAL_ACTUALIZADO = "historial.actualizado";
    public static final String RK_HISTORIAL_ELIMINADO = "historial.eliminado";

    /** Patrón con el que este servicio se suscribe a los eventos de pacientes. */
    public static final String PATRON_EVENTOS_PACIENTES = "paciente.#";

    /** Routing key para que OTROS pregunten a este servicio. */
    public static final String RK_RPC_HISTORIAL = "historial.consulta";

    /** Routing key con la que ESTE servicio pregunta a pacientes-service. */
    public static final String RK_RPC_PACIENTES = "pacientes.consulta";

    // ---------- Argumentos de las colas ----------
    /**
     * Cuánto se conservan los mensajes descartados. Sin límite, un bucle de
     * mensajes defectuosos acabaría llenando el disco del broker.
     *
     * OJO: debe coincidir con el TTL_DLQ_MS de los otros servicios (incluido el
     * topology.py de FastAPI) o RabbitMQ cierra el canal con PRECONDITION_FAILED.
     */
    public static final int TTL_DLQ_MS = 24 * 60 * 60 * 1000;

    @Bean
    public TopicExchange eventosExchange() {
        return new TopicExchange(EXCHANGE_EVENTOS, true, false);
    }

    @Bean
    public DirectExchange rpcExchange() {
        return new DirectExchange(EXCHANGE_RPC, true, false);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue dlq() {
        return QueueBuilder.durable(QUEUE_DLQ)
                .ttl(TTL_DLQ_MS)
                .build();
    }

    /** El "#" captura cualquier routing key, así una sola DLQ recoge todo. */
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlq()).to(dlxExchange()).with("#");
    }

    @Bean
    public Queue eventosPacientesQueue() {
        return QueueBuilder.durable(QUEUE_EVENTOS_PACIENTES)
                .deadLetterExchange(EXCHANGE_DLX)
                .build();
    }

    @Bean
    public Binding eventosPacientesBinding() {
        return BindingBuilder.bind(eventosPacientesQueue())
                .to(eventosExchange())
                .with(PATRON_EVENTOS_PACIENTES);
    }

    @Bean
    public Queue rpcHistorialQueue() {
        return QueueBuilder.durable(QUEUE_RPC_HISTORIAL).build();
    }

    @Bean
    public Binding rpcHistorialBinding() {
        return BindingBuilder.bind(rpcHistorialQueue())
                .to(rpcExchange())
                .with(RK_RPC_HISTORIAL);
    }

    /**
     * Mismo converter que en pacientes-service, y por los mismos dos motivos:
     *
     * - TypePrecedence.INFERRED evita que el converter exija en el receptor la
     *   misma clase Java del emisor (viaja en la cabecera "__TypeId__"). Aquí
     *   los paquetes son com.salud.historial y allí com.salud.pacientes.
     *
     * - FAIL_ON_UNKNOWN_PROPERTIES desactivado permite recibir mensajes con más
     *   campos de los que este servicio conoce.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        JsonMapper mapper = JsonMapper.builder()
                // En Jackson 3 esta opción vive en DateTimeFeature, no en
                // SerializationFeature como en Jackson 2.
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(mapper);
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
