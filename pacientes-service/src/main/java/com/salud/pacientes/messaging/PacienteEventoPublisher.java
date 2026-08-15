package com.salud.pacientes.messaging;

import com.salud.pacientes.config.KafkaConfig;
import com.salud.pacientes.dto.evento.PacienteEvento;
import com.salud.pacientes.model.Paciente;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * Publica en el topic "salud.pacientes" cada cambio sobre un paciente.
 *
 * Publicar es fire-and-forget: no se sabe ni importa quién lee. Hoy leen
 * historial-medico-service y pacientes-fastapi-service; mañana puede sumarse
 * otro consumidor sin tocar esta clase, y además podrá leer desde el offset 0
 * para ponerse al día con todo lo que se publicó antes de que existiera.
 *
 * IMPORTANTE: el envío se aplaza hasta DESPUÉS del commit de PostgreSQL.
 * Ver el comentario de {@link #publicar}.
 */
@Component
@RequiredArgsConstructor
public class PacienteEventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(PacienteEventoPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public void publicarCreado(Paciente paciente) {
        publicar(KafkaConfig.EV_PACIENTE_CREADO, paciente);
    }

    public void publicarActualizado(Paciente paciente) {
        publicar(KafkaConfig.EV_PACIENTE_ACTUALIZADO, paciente);
    }

    /**
     * El borrado NO manda un evento con datos: manda un "tombstone" (lápida),
     * que es un mensaje con la clave del paciente y el cuerpo NULO.
     *
     * Es la forma canónica de borrar en un topic compactado. La compactación
     * conserva el último valor de cada clave, así que si ese último valor es
     * nulo, Kafka entiende que la entidad dejó de existir y con el tiempo la
     * elimina del log por completo. Si en su lugar se mandara un evento normal
     * de tipo "eliminado", ese registro quedaría en el topic para siempre.
     *
     * Los consumidores no pierden nada: para borrar su copia solo necesitan el
     * id, y el id ES la clave del mensaje.
     */
    public void publicarEliminado(Paciente paciente) {
        enviarTrasCommit(String.valueOf(paciente.getId()), null,
                KafkaConfig.EV_PACIENTE_ELIMINADO, paciente.getId());
    }

    /**
     * Construye el evento AHORA y lo envía DESPUÉS del commit.
     *
     * Los dos tiempos son distintos a propósito:
     *
     * - Se construye ya, dentro de la transacción, porque la entidad todavía
     *   está cargada.
     *
     * - Se envía después del commit porque publicar dentro de la transacción es
     *   el clásico problema de la "doble escritura": si el commit falla más
     *   tarde, el evento YA salió y los demás servicios se quedan con la réplica
     *   de un paciente que nunca se guardó. En Kafka esto duele más que en
     *   RabbitMQ, porque el evento erróneo queda en el log de forma permanente
     *   y lo volverá a leer cualquier consumidor que reproduzca la historia.
     */
    private void publicar(String tipo, Paciente paciente) {
        PacienteEvento evento = new PacienteEvento(
                tipo,
                paciente.getId(),
                paciente.getNombre(),
                paciente.getApellido(),
                paciente.getEmail(),
                paciente.getTelefono(),
                paciente.getFechaNacimiento(),
                paciente.getDireccion(),
                paciente.getTipoDocumento(),
                paciente.getNumeroDocumento(),
                LocalDateTime.now()
        );

        enviarTrasCommit(String.valueOf(paciente.getId()), evento, tipo, paciente.getId());
    }

    private void enviarTrasCommit(String clave, PacienteEvento evento, String tipo, Long pacienteId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(clave, evento, tipo, pacienteId);
                }
            });
        } else {
            enviar(clave, evento, tipo, pacienteId);
        }
    }

    private void enviar(String clave, PacienteEvento evento, String tipo, Long pacienteId) {
        try {
            // La clave (el id del paciente) decide la partición. Todos los
            // eventos del mismo paciente van juntos y en orden.
            kafkaTemplate.send(KafkaConfig.TOPIC_PACIENTES, clave, evento);
            log.debug("Evento '{}' publicado para el paciente {} (clave={})", tipo, pacienteId, clave);
        } catch (Exception e) {
            // Que el broker esté caído no debe tumbar la operación principal: el
            // paciente ya está confirmado en PostgreSQL. Se registra para dejar
            // rastro de que ese evento nunca salió.
            //
            // Nota para la clase: esto es lo máximo que se puede hacer sin un
            // patrón "outbox" (guardar el evento en la misma transacción y que
            // un proceso aparte lo publique). Aquí se prefiere la simplicidad.
            log.error("No se pudo publicar el evento '{}' del paciente {}: {}",
                    tipo, pacienteId, e.getMessage(), e);
        }
    }
}
