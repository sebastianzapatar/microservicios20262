package com.salud.pacientes.messaging;

import com.salud.pacientes.config.RabbitMQConfig;
import com.salud.pacientes.dto.evento.PacienteEvento;
import com.salud.pacientes.model.Paciente;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * Publica en el exchange "salud.events" cada cambio sobre un paciente.
 *
 * Publicar es fire-and-forget: no se sabe ni importa quién escucha. Hoy escuchan
 * historial-medico-service y pacientes-fastapi-service; mañana puede sumarse
 * otro consumidor sin tocar esta clase.
 *
 * IMPORTANTE: el envío se aplaza hasta DESPUÉS del commit de PostgreSQL. Ver
 * el comentario de {@link #publicar} para el porqué.
 */
@Component
@RequiredArgsConstructor
public class PacienteEventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(PacienteEventoPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public void publicarCreado(Paciente paciente) {
        publicar(RabbitMQConfig.RK_PACIENTE_CREADO, paciente);
    }

    public void publicarActualizado(Paciente paciente) {
        publicar(RabbitMQConfig.RK_PACIENTE_ACTUALIZADO, paciente);
    }

    public void publicarEliminado(Paciente paciente) {
        publicar(RabbitMQConfig.RK_PACIENTE_ELIMINADO, paciente);
    }

    /**
     * Construye el evento AHORA y lo envía DESPUÉS del commit.
     *
     * Los dos tiempos importan y son distintos a propósito:
     *
     * - Se construye ya, dentro de la transacción, porque la entidad todavía
     *   está cargada. En "paciente.eliminado" es imprescindible: después del
     *   commit la fila ya no existe y no habría de dónde sacar los datos.
     *
     * - Se envía después del commit porque publicar dentro de la transacción es
     *   el clásico problema de la "doble escritura": si el commit falla más
     *   tarde, el evento YA salió y los demás servicios se quedan con la réplica
     *   de un paciente que nunca se guardó. En el borrado es peor todavía: los
     *   consumidores borrarían su copia de un paciente que sigue en PostgreSQL.
     *
     * Si no hay transacción activa (por ejemplo, si algún día se llama desde un
     * método sin @Transactional), se envía en el acto.
     */
    private void publicar(String routingKey, Paciente paciente) {
        PacienteEvento evento = new PacienteEvento(
                routingKey,
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

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(evento);
                }
            });
        } else {
            enviar(evento);
        }
    }

    private void enviar(PacienteEvento evento) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EVENTOS, evento.tipo(), evento);
            log.debug("Evento '{}' publicado para el paciente {}", evento.tipo(), evento.pacienteId());
        } catch (Exception e) {
            // Que el broker esté caído no debe tumbar la operación principal: el
            // paciente ya está confirmado en PostgreSQL. Se registra para dejar
            // rastro de que ese evento nunca salió.
            //
            // Nota para la clase: esto es lo máximo que se puede hacer sin un
            // patrón "outbox" (guardar el evento en la misma transacción y que
            // un proceso aparte lo envíe). Aquí se prefiere la simplicidad.
            log.error("No se pudo publicar el evento '{}' del paciente {}: {}",
                    evento.tipo(), evento.pacienteId(), e.getMessage(), e);
        }
    }
}
