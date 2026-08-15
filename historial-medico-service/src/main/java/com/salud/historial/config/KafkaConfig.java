package com.salud.historial.config;

import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Topología de Kafka vista desde historial-medico-service. Es el espejo de la
 * de pacientes-service: los topics son LOS MISMOS y lo que cambia es quién
 * escribe, quién lee y con qué consumer group.
 *
 * Aquí se ve la diferencia más importante con RabbitMQ. Con colas, cada
 * servicio necesitaba SU PROPIA cola para recibir una copia del mensaje; si dos
 * compartían cola, el broker les repartía los mensajes en vez de entregárselos
 * a los dos. En Kafka nadie necesita su propio topic: los tres servicios leen
 * el mismo y lo que los separa es el CONSUMER GROUP, porque cada grupo lleva
 * sus propios offsets.
 *
 * Declarar los mismos topics desde varios servicios es seguro: si ya existen,
 * Kafka no hace nada. (Ojo: solo se ignora la creación. Cambiar el número de
 * particiones de un topic ya existente sí requiere hacerlo a mano.)
 */
@Configuration
public class KafkaConfig {

    // ---------- Topics (compartidos) ----------
    /** Eventos de pacientes. Los escribe pacientes-service; aquí se leen. */
    public static final String TOPIC_PACIENTES = "salud.pacientes";

    /** Eventos de historiales. Este servicio es el DUEÑO y el único que escribe. */
    public static final String TOPIC_HISTORIALES = "salud.historiales";

    /** Dead Letter Topic de lo que este servicio no logra procesar. */
    public static final String TOPIC_PACIENTES_DLT = TOPIC_PACIENTES + "-dlt";

    // ---------- Consumer group ----------
    /** Grupo propio: distinto al de los demás servicios, con sus propios offsets. */
    public static final String GRUPO = "historial-medico-service";

    // ---------- Tipos de evento ----------
    public static final String EV_HISTORIAL_CREADO = "historial.creado";
    public static final String EV_HISTORIAL_ACTUALIZADO = "historial.actualizado";
    public static final String EV_HISTORIAL_ELIMINADO = "historial.eliminado";

    public static final String EV_PACIENTE_CREADO = "paciente.creado";
    public static final String EV_PACIENTE_ACTUALIZADO = "paciente.actualizado";
    public static final String EV_PACIENTE_ELIMINADO = "paciente.eliminado";

    private static final int PARTICIONES = 3;
    private static final short REPLICAS = 1;
    private static final String RETENCION_DLT_MS = String.valueOf(7L * 24 * 60 * 60 * 1000);

    /**
     * Los dos topics de negocio son COMPACTADOS: Kafka conserva para siempre,
     * como mínimo, el último mensaje de cada clave. Así el topic funciona además
     * como "tabla del estado actual" y la réplica local se puede reconstruir
     * entera leyendo desde el offset 0.
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

    @Bean
    public org.apache.kafka.clients.admin.NewTopic topicPacientesDlt() {
        return TopicBuilder.name(TOPIC_PACIENTES_DLT)
                .partitions(PARTICIONES)
                .replicas(REPLICAS)
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENCION_DLT_MS)
                .build();
    }

    /**
     * Reintenta 3 veces con espera creciente y, si aun así falla, manda el
     * mensaje al topic "-dlt" y AVANZA el offset.
     *
     * Ese avance es lo importante y es muy distinto de RabbitMQ: allí se podía
     * rechazar un mensaje concreto y seguir con los demás. Aquí el offset es un
     * único número que avanza en orden, así que un mensaje que nunca se
     * confirma BLOQUEA toda la partición detrás de él.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recuperador = new DeadLetterPublishingRecoverer(kafkaTemplate);

        ExponentialBackOff espera = new ExponentialBackOff(1000L, 2.0);
        espera.setMaxAttempts(3);

        return new DefaultErrorHandler(recuperador, espera);
    }
}
