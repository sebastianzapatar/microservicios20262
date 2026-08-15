package com.salud.historial.messaging;

import com.salud.historial.config.KafkaConfig;
import com.salud.historial.dto.evento.PacienteEvento;
import com.salud.historial.model.PacienteReplica;
import com.salud.historial.repository.PacienteReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Consume el topic "salud.pacientes" y mantiene con él la colección
 * pacientes_replica (ver {@link PacienteReplica}).
 *
 * Esto es lo que sustituye a la llamada HTTP que antes hacía este servicio para
 * validar que un paciente existe: en vez de preguntarlo cada vez, se le va
 * contando cada alta, cambio y baja a medida que ocurren.
 *
 * No lleva @Transactional (y su equivalente en pacientes-service sí): allí hay
 * JPA sobre PostgreSQL, y aquí MongoDB en modo standalone, que no admite
 * transacciones ni tiene un gestor declarado. Tampoco hace falta: cada evento
 * se resuelve con UNA sola operación sobre Mongo, que ya es atómica.
 */
@Component
@RequiredArgsConstructor
public class PacienteEventoListener {

    private static final Logger log = LoggerFactory.getLogger(PacienteEventoListener.class);

    private final PacienteReplicaRepository replicaRepository;

    @KafkaListener(topics = KafkaConfig.TOPIC_PACIENTES, groupId = KafkaConfig.GRUPO)
    public void recibir(ConsumerRecord<String, PacienteEvento> registro) {
        String clave = registro.key();
        PacienteEvento evento = registro.value();

        log.debug("Mensaje recibido de '{}' particion={} offset={} clave={}",
                registro.topic(), registro.partition(), registro.offset(), clave);

        if (clave == null) {
            log.warn("Mensaje sin clave en el topic de pacientes, se descarta");
            return;
        }

        Long pacienteId;
        try {
            pacienteId = Long.valueOf(clave);
        } catch (NumberFormatException e) {
            log.warn("Clave no numérica en el topic de pacientes: '{}', se descarta", clave);
            return;
        }

        // Cuerpo nulo = tombstone = "este paciente dejó de existir".
        if (evento == null) {
            replicaRepository.deleteById(pacienteId);
            log.debug("Paciente {} eliminado de la réplica local (tombstone)", pacienteId);
            return;
        }

        switch (String.valueOf(evento.tipo())) {
            case KafkaConfig.EV_PACIENTE_CREADO, KafkaConfig.EV_PACIENTE_ACTUALIZADO -> guardar(pacienteId, evento);
            default -> log.warn("Tipo de evento de paciente no reconocido: {}", evento.tipo());
        }
    }

    /**
     * save() sobre un id que ya existe reemplaza el documento, así que
     * reprocesar el mismo evento deja el mismo resultado.
     *
     * Esto no es un detalle menor en Kafka: cualquiera puede rebobinar el
     * consumer group al offset 0 para reconstruir la réplica, y entonces TODOS
     * los eventos se vuelven a procesar. Si la operación no fuese idempotente,
     * esa reconstrucción duplicaría datos en lugar de rehacerlos.
     */
    private void guardar(Long pacienteId, PacienteEvento evento) {
        replicaRepository.save(PacienteReplica.builder()
                .id(pacienteId)
                .nombre(evento.nombre())
                .apellido(evento.apellido())
                .email(evento.email())
                .telefono(evento.telefono())
                .direccion(evento.direccion())
                .tipoDocumento(evento.tipoDocumento())
                .numeroDocumento(evento.numeroDocumento())
                .fechaSincronizacion(LocalDateTime.now())
                .build());
    }
}
