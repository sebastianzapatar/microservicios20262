package com.salud.pacientes.config;

import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Topología de Kafka vista desde pacientes-service.
 *
 * DIFERENCIA CLAVE CON RABBITMQ, y es la que explica todo lo demás:
 * en una cola el mensaje se BORRA cuando alguien lo consume. En Kafka el
 * mensaje se queda en un LOG y cada consumidor lleva su propio marcador
 * (offset) de por dónde va. Nadie "vacía" nada.
 *
 * De ahí salen tres consecuencias que se aprovechan en esta rama:
 *
 * 1) No hacen falta colas distintas por servicio. Todos leen el MISMO topic;
 *    lo que los separa es el consumer group. Cada grupo tiene sus propios
 *    offsets, así que los tres servicios reciben los tres mensajes.
 *
 * 2) Un servicio nuevo puede empezar a leer desde el principio del log
 *    (auto-offset-reset: earliest) y reconstruir su réplica local con TODA la
 *    historia. Por eso esta rama NO necesita el patrón RPC que sí tenía la
 *    rama de RabbitMQ: allí la cola arrancaba vacía y había que preguntarle al
 *    otro servicio por los datos anteriores.
 *
 * 3) La CLAVE del mensaje decide la partición, y Kafka solo garantiza el orden
 *    DENTRO de una partición. Por eso se usa el id de la entidad como clave:
 *    así todos los eventos de un mismo paciente caen en la misma partición y se
 *    procesan en el orden en que ocurrieron.
 *
 * La conexión y la serialización se configuran en application.yml, no aquí:
 * son propiedades, y verlas juntas en un solo sitio se lee mejor.
 */
@Configuration
public class KafkaConfig {

    // ---------- Topics ----------
    /** Eventos de pacientes. Este servicio es el DUEÑO y el único que escribe. */
    public static final String TOPIC_PACIENTES = "salud.pacientes";

    /** Eventos de historiales. Los escribe historial-medico-service; aquí se leen. */
    public static final String TOPIC_HISTORIALES = "salud.historiales";

    /**
     * Adonde van los mensajes que no se pudieron procesar. El sufijo "-dlt"
     * (Dead Letter Topic) es la convención por defecto de Spring Kafka y es el
     * equivalente de la DLQ que se usó con RabbitMQ.
     */
    public static final String TOPIC_HISTORIALES_DLT = TOPIC_HISTORIALES + "-dlt";

    // ---------- Consumer group ----------
    /**
     * Identifica a ESTE servicio como lector. Kafka guarda los offsets por
     * grupo: si el servicio se cae y vuelve, retoma donde iba. Y si se levantan
     * varias instancias con el mismo grupo, Kafka les reparte las particiones
     * entre ellas: ahí está el escalado horizontal.
     */
    public static final String GRUPO = "pacientes-service";

    // ---------- Tipos de evento ----------
    // Viajan dentro del cuerpo del mensaje, en el campo "tipo", para que el
    // mensaje se explique solo sin mirar cabeceras.
    public static final String EV_PACIENTE_CREADO = "paciente.creado";
    public static final String EV_PACIENTE_ACTUALIZADO = "paciente.actualizado";
    public static final String EV_PACIENTE_ELIMINADO = "paciente.eliminado";

    public static final String EV_HISTORIAL_CREADO = "historial.creado";
    public static final String EV_HISTORIAL_ACTUALIZADO = "historial.actualizado";
    public static final String EV_HISTORIAL_ELIMINADO = "historial.eliminado";

    /**
     * Particiones de cada topic. Con 3, el trabajo puede repartirse entre hasta
     * 3 instancias del mismo servicio. Tener más particiones que consumidores
     * está bien; al revés, los consumidores sobrantes se quedan ociosos.
     */
    private static final int PARTICIONES = 3;

    /** Con un solo broker no se puede replicar. En producción esto sería 3. */
    private static final short REPLICAS = 1;

    private static final String RETENCION_DLT_MS = String.valueOf(7L * 24 * 60 * 60 * 1000);

    /**
     * Los dos topics de negocio son COMPACTADOS (cleanup.policy=compact).
     *
     * Compactar significa que Kafka conserva para siempre, como mínimo, el
     * ÚLTIMO mensaje de cada clave. El topic deja de ser solo "una lista de
     * cosas que pasaron" y se convierte además en "una tabla con el estado
     * actual de cada entidad".
     *
     * Eso es lo que permite reconstruir la réplica local entera leyendo el log
     * desde cero, por antiguo que sea el paciente. Con la política por defecto
     * (delete) los mensajes se borrarían a los 7 días y la reconstrucción
     * quedaría incompleta.
     */
    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicPacientes() {
        return TopicBuilder.name(TOPIC_PACIENTES)
                .partitions(PARTICIONES)
                .replicas(REPLICAS)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicHistoriales() {
        return TopicBuilder.name(TOPIC_HISTORIALES)
                .partitions(PARTICIONES)
                .replicas(REPLICAS)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
    }

    /**
     * El DLT no se compacta: aquí interesa el historial completo de fallos, no
     * el último por clave. Se le pone retención de 7 días para que no crezca
     * sin límite.
     */
    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicHistorialesDlt() {
        return TopicBuilder.name(TOPIC_HISTORIALES_DLT)
                .partitions(PARTICIONES)
                .replicas(REPLICAS)
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENCION_DLT_MS)
                .build();
    }

    /**
     * Qué hacer cuando el listener lanza una excepción.
     *
     * Reintenta 3 veces con espera creciente y, si aun así falla, publica el
     * mensaje en el topic "-dlt" y AVANZA el offset.
     *
     * Ese avance es lo importante y es muy distinto de RabbitMQ: allí se podía
     * rechazar un mensaje concreto y seguir con los demás. Aquí el offset es un
     * único número que avanza en orden, así que un mensaje que nunca se
     * confirma BLOQUEA toda la partición detrás de él. Mandarlo al DLT y seguir
     * es la única forma de no atascarse.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recuperador = new DeadLetterPublishingRecoverer(kafkaTemplate);

        ExponentialBackOff espera = new ExponentialBackOff(1000L, 2.0);
        espera.setMaxAttempts(3);

        return new DefaultErrorHandler(recuperador, espera);
    }
}
