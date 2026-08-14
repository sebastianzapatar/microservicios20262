package com.salud.pacientes.config;

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
 * Topología de RabbitMQ vista desde pacientes-service.
 *
 * Se usan DOS patrones distintos, a propósito:
 *
 * 1) EVENTOS (asíncrono, fire-and-forget) sobre el exchange topic "salud.events".
 *    Este servicio PUBLICA "paciente.creado/actualizado/eliminado" y no espera
 *    respuesta de nadie. Además CONSUME "historial.*" para mantener una copia
 *    local de solo lectura (tabla historial_resumen) y poder responder sin
 *    hablar con el otro microservicio.
 *
 * 2) RPC (petición/respuesta) sobre el exchange direct "salud.rpc".
 *    Este servicio ATIENDE la cola "pacientes.rpc" (le preguntan si un paciente
 *    existe) y PREGUNTA a la cola "historial.rpc" cuando necesita el historial
 *    al instante. Sigue siendo bloqueante, pero el transporte ya es la cola:
 *    ni se conoce la URL del otro servicio ni hace falta Eureka para esto.
 *
 * Cada declaración debe coincidir EXACTAMENTE con la de los demás servicios
 * (mismo durable/autoDelete). Si dos servicios declaran el mismo recurso con
 * argumentos distintos, RabbitMQ cierra el canal con PRECONDITION_FAILED.
 */
@Configuration
public class RabbitMQConfig {

    // ---------- Exchanges ----------
    public static final String EXCHANGE_EVENTOS = "salud.events";
    public static final String EXCHANGE_RPC = "salud.rpc";
    public static final String EXCHANGE_DLX = "salud.dlx";

    // ---------- Colas ----------
    /** Cola de descarte compartida: los mensajes que fallan acaban aquí. */
    public static final String QUEUE_DLQ = "salud.dlq";

    /** Cola propia: aquí caen los eventos publicados por historial-medico-service. */
    public static final String QUEUE_EVENTOS_HISTORIAL = "pacientes.eventos.historial";

    /** Cola propia: aquí llegan las preguntas RPC dirigidas a este servicio. */
    public static final String QUEUE_RPC_PACIENTES = "pacientes.rpc";

    // ---------- Routing keys ----------
    public static final String RK_PACIENTE_CREADO = "paciente.creado";
    public static final String RK_PACIENTE_ACTUALIZADO = "paciente.actualizado";
    public static final String RK_PACIENTE_ELIMINADO = "paciente.eliminado";

    /** Patrón con el que este servicio se suscribe a los eventos del historial. */
    public static final String PATRON_EVENTOS_HISTORIAL = "historial.#";

    /** Routing key para que OTROS pregunten a este servicio. */
    public static final String RK_RPC_PACIENTES = "pacientes.consulta";

    /** Routing key con la que ESTE servicio pregunta a historial-medico-service. */
    public static final String RK_RPC_HISTORIAL = "historial.consulta";

    // ---------- Argumentos de las colas ----------
    /**
     * Cuánto se conservan los mensajes descartados. Sin límite, un bucle de
     * mensajes defectuosos acabaría llenando el disco del broker. 24 h da tiempo
     * de sobra a inspeccionar la DLQ desde la consola web.
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

    /**
     * Cola de eventos del historial. Si el consumidor falla y agota los
     * reintentos, el mensaje se enruta al DLX en lugar de perderse o quedar
     * reencolándose para siempre (para eso está default-requeue-rejected: false).
     */
    @Bean
    public Queue eventosHistorialQueue() {
        return QueueBuilder.durable(QUEUE_EVENTOS_HISTORIAL)
                .deadLetterExchange(EXCHANGE_DLX)
                .build();
    }

    @Bean
    public Binding eventosHistorialBinding() {
        return BindingBuilder.bind(eventosHistorialQueue())
                .to(eventosExchange())
                .with(PATRON_EVENTOS_HISTORIAL);
    }

    /**
     * Cola RPC de este servicio. Sin DLX: si algo falla, quien preguntó se
     * enterará por el timeout de su propia espera, no por un descarte.
     */
    @Bean
    public Queue rpcPacientesQueue() {
        return QueueBuilder.durable(QUEUE_RPC_PACIENTES).build();
    }

    @Bean
    public Binding rpcPacientesBinding() {
        return BindingBuilder.bind(rpcPacientesQueue())
                .to(rpcExchange())
                .with(RK_RPC_PACIENTES);
    }

    /**
     * Convierte los mensajes a/desde JSON.
     *
     * Spring Boot detecta este bean y lo inyecta solo en el RabbitTemplate y en
     * la factory de @RabbitListener, así que no hace falta declarar ninguno de
     * los dos a mano.
     *
     * Los dos ajustes del final son los que hacen que esto funcione entre
     * servicios distintos:
     *
     * - TypePrecedence.INFERRED: por defecto el converter escribe la clase Java
     *   del emisor en la cabecera "__TypeId__" y exige esa MISMA clase en el
     *   receptor. Como cada microservicio usa su propio paquete (com.salud.pacientes
     *   vs com.salud.historial) y FastAPI no tiene clases Java, se le indica que
     *   deduzca el tipo del parámetro del método que recibe el mensaje.
     *
     * - FAIL_ON_UNKNOWN_PROPERTIES desactivado: permite que el emisor mande más
     *   campos de los que el receptor conoce. Así se puede añadir información a
     *   un evento sin desplegar todos los servicios a la vez.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        JsonMapper mapper = JsonMapper.builder()
                // Fechas como texto ISO-8601 ("2026-08-14T10:30:00") y no como
                // número, para que el consumidor de Python las entienda.
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
