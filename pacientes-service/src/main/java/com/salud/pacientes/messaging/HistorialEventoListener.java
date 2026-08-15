package com.salud.pacientes.messaging;

import com.salud.pacientes.config.KafkaConfig;
import com.salud.pacientes.dto.evento.HistorialEvento;
import com.salud.pacientes.model.HistorialResumen;
import com.salud.pacientes.repository.HistorialResumenRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consume el topic "salud.historiales" y mantiene con él la tabla
 * historial_resumen (ver {@link HistorialResumen}).
 *
 * Esto es lo que sustituye a la llamada HTTP que antes hacía este servicio:
 * en vez de preguntar "¿qué historiales tiene el paciente 4?" cuando le
 * consultan, se le va contando cada cambio a medida que ocurre.
 *
 * El parámetro es un ConsumerRecord entero y no solo el evento, por dos motivos
 * que conviene enseñar:
 *
 *  - Da acceso a la CLAVE, que es la única información que traen los mensajes
 *    de borrado (tombstones).
 *  - Deja ver la partición y el offset en el log, que es la forma más directa
 *    de entender cómo Kafka reparte y ordena los mensajes.
 */
@Component
@RequiredArgsConstructor
public class HistorialEventoListener {

    private static final Logger log = LoggerFactory.getLogger(HistorialEventoListener.class);

    private final HistorialResumenRepository resumenRepository;

    @KafkaListener(topics = KafkaConfig.TOPIC_HISTORIALES, groupId = KafkaConfig.GRUPO)
    @Transactional
    public void recibir(ConsumerRecord<String, HistorialEvento> registro) {
        String historialId = registro.key();
        HistorialEvento evento = registro.value();

        log.debug("Mensaje recibido de '{}' particion={} offset={} clave={}",
                registro.topic(), registro.partition(), registro.offset(), historialId);

        if (historialId == null) {
            log.warn("Mensaje sin clave en el topic de historiales, se descarta");
            return;
        }

        // Cuerpo nulo = tombstone = "este historial dejó de existir".
        if (evento == null) {
            resumenRepository.deleteById(historialId);
            log.debug("Historial {} eliminado de la réplica local (tombstone)", historialId);
            return;
        }

        switch (String.valueOf(evento.tipo())) {
            case KafkaConfig.EV_HISTORIAL_CREADO, KafkaConfig.EV_HISTORIAL_ACTUALIZADO -> guardar(historialId, evento);
            default -> log.warn("Tipo de evento de historial no reconocido: {}", evento.tipo());
        }
    }

    /**
     * save() sobre un id que ya existe actualiza la fila, así que reprocesar el
     * mismo evento dos veces deja el mismo resultado.
     *
     * Esto no es un detalle menor en Kafka: cualquiera puede rebobinar el
     * consumer group al offset 0 para reconstruir la tabla, y entonces TODOS los
     * eventos se vuelven a procesar. Si la operación no fuese idempotente, esa
     * reconstrucción duplicaría datos en lugar de rehacerlos.
     */
    private void guardar(String historialId, HistorialEvento evento) {
        resumenRepository.save(HistorialResumen.builder()
                .id(historialId)
                .pacienteId(evento.pacienteId())
                .diagnostico(evento.diagnostico())
                .tratamiento(evento.tratamiento())
                .medico(evento.medico())
                .fechaConsulta(evento.fechaConsulta())
                .notas(evento.notas())
                .tipoConsulta(evento.tipoConsulta())
                .fechaSincronizacion(LocalDateTime.now())
                .build());
    }
}
