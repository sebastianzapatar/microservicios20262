package com.salud.historial.messaging;

import com.salud.historial.config.RabbitMQConfig;
import com.salud.historial.dto.evento.PacienteEvento;
import com.salud.historial.model.PacienteReplica;
import com.salud.historial.repository.PacienteReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Consume los eventos "paciente.*" y mantiene con ellos la colección
 * pacientes_replica (ver {@link PacienteReplica}).
 *
 * Esto es lo que sustituye a la llamada HTTP que antes hacía este servicio para
 * validar que un paciente existe: en vez de preguntarlo cada vez, se le va
 * contando cada alta, cambio y baja a medida que ocurren.
 *
 * El listener equivalente de pacientes-service lleva @Transactional y este no.
 * No es un descuido: allí hay JPA sobre PostgreSQL, y aquí MongoDB en modo
 * standalone, que no admite transacciones (exigen un replica set) ni tiene un
 * gestor declarado. Anotar este método haría fallar cada mensaje. Tampoco hace
 * falta: cada evento se resuelve con UNA sola operación sobre Mongo, que ya es
 * atómica por sí misma.
 */
@Component
@RequiredArgsConstructor
public class PacienteEventoListener {

    private static final Logger log = LoggerFactory.getLogger(PacienteEventoListener.class);

    private final PacienteReplicaRepository replicaRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_EVENTOS_PACIENTES)
    public void recibir(PacienteEvento evento) {
        if (evento.pacienteId() == null) {
            log.warn("Evento de paciente sin pacienteId, se descarta: {}", evento);
            return;
        }

        log.debug("Evento '{}' recibido para el paciente {}", evento.tipo(), evento.pacienteId());

        // El "tipo" viene en el cuerpo del mensaje y repite la routing key.
        switch (String.valueOf(evento.tipo())) {
            case "paciente.creado", "paciente.actualizado" -> guardar(evento);
            case "paciente.eliminado" -> replicaRepository.deleteById(evento.pacienteId());
            default -> log.warn("Tipo de evento de paciente no reconocido: {}", evento.tipo());
        }
    }

    /**
     * save() sobre un id que ya existe reemplaza el documento, así que
     * reprocesar el mismo evento deja el mismo resultado. Importa porque RabbitMQ
     * garantiza "al menos una vez", no "exactamente una vez".
     */
    private void guardar(PacienteEvento evento) {
        replicaRepository.save(PacienteReplica.builder()
                .id(evento.pacienteId())
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
