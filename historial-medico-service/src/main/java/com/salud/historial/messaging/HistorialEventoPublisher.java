package com.salud.historial.messaging;

import com.salud.historial.config.RabbitMQConfig;
import com.salud.historial.dto.evento.HistorialEvento;
import com.salud.historial.model.HistorialMedico;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publica en el exchange "salud.events" cada cambio sobre un historial médico.
 *
 * Quien escucha hoy es pacientes-service (para su tabla historial_resumen) y
 * pacientes-fastapi-service (para sus notificaciones). Este servicio no lo sabe
 * ni le hace falta: publica y sigue.
 *
 * A diferencia de PacienteEventoPublisher, aquí NO hace falta aplazar el envío
 * hasta después de un commit. Este servicio usa MongoDB en modo standalone y no
 * declara ningún gestor de transacciones, así que cuando save() retorna el
 * documento ya está escrito: publicar a continuación no puede adelantarse a un
 * commit que no existe. Si algún día se añadieran transacciones de Mongo (que
 * exigen un replica set), habría que copiar aquí el aplazamiento del otro
 * servicio para no caer en el problema de la doble escritura.
 */
@Component
@RequiredArgsConstructor
public class HistorialEventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(HistorialEventoPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public void publicarCreado(HistorialMedico historial) {
        publicar(RabbitMQConfig.RK_HISTORIAL_CREADO, historial);
    }

    public void publicarActualizado(HistorialMedico historial) {
        publicar(RabbitMQConfig.RK_HISTORIAL_ACTUALIZADO, historial);
    }

    public void publicarEliminado(HistorialMedico historial) {
        publicar(RabbitMQConfig.RK_HISTORIAL_ELIMINADO, historial);
    }

    private void publicar(String routingKey, HistorialMedico historial) {
        HistorialEvento evento = new HistorialEvento(
                routingKey,
                historial.getId(),
                historial.getPacienteId(),
                historial.getDiagnostico(),
                historial.getTratamiento(),
                historial.getMedico(),
                historial.getFechaConsulta(),
                historial.getNotas(),
                historial.getTipoConsulta(),
                LocalDateTime.now()
        );

        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EVENTOS, routingKey, evento);
            log.debug("Evento '{}' publicado para el historial {}", routingKey, historial.getId());
        } catch (Exception e) {
            // El historial ya está guardado en MongoDB; que el broker esté caído
            // no debe deshacer la operación. Se registra para dejar rastro de
            // que ese evento nunca salió.
            log.error("No se pudo publicar el evento '{}' del historial {}: {}",
                    routingKey, historial.getId(), e.getMessage(), e);
        }
    }
}
