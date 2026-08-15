package com.salud.historial.messaging;

import com.salud.historial.config.KafkaConfig;
import com.salud.historial.dto.evento.HistorialEvento;
import com.salud.historial.model.HistorialMedico;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publica en el topic "salud.historiales" cada cambio sobre un historial médico.
 *
 * Quien lee hoy es pacientes-service (para su tabla historial_resumen) y
 * pacientes-fastapi-service (para sus notificaciones). Este servicio no lo sabe
 * ni le hace falta: publica y sigue.
 *
 * A diferencia de PacienteEventoPublisher en pacientes-service, aquí NO hace
 * falta aplazar el envío hasta después de un commit. Este servicio usa MongoDB
 * en modo standalone y no declara ningún gestor de transacciones, así que
 * cuando save() retorna el documento ya está escrito: publicar a continuación
 * no puede adelantarse a un commit que no existe. Si algún día se añadieran
 * transacciones de Mongo (que exigen un replica set), habría que copiar aquí el
 * aplazamiento del otro servicio para no caer en la doble escritura.
 */
@Component
@RequiredArgsConstructor
public class HistorialEventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(HistorialEventoPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public void publicarCreado(HistorialMedico historial) {
        publicar(KafkaConfig.EV_HISTORIAL_CREADO, historial);
    }

    public void publicarActualizado(HistorialMedico historial) {
        publicar(KafkaConfig.EV_HISTORIAL_ACTUALIZADO, historial);
    }

    /**
     * El borrado manda un "tombstone": un mensaje con la clave del historial y
     * el cuerpo NULO.
     *
     * Es la forma canónica de borrar en un topic compactado. La compactación
     * conserva el último valor de cada clave; si ese valor es nulo, Kafka
     * entiende que la entidad dejó de existir y acaba eliminándola del log. Si
     * en su lugar se mandara un evento normal de tipo "eliminado", ese registro
     * quedaría en el topic para siempre.
     *
     * Los consumidores no pierden nada: para borrar su copia solo necesitan el
     * id, y el id ES la clave del mensaje.
     */
    public void publicarEliminado(HistorialMedico historial) {
        enviar(historial.getId(), null, KafkaConfig.EV_HISTORIAL_ELIMINADO);
    }

    private void publicar(String tipo, HistorialMedico historial) {
        HistorialEvento evento = new HistorialEvento(
                tipo,
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

        enviar(historial.getId(), evento, tipo);
    }

    private void enviar(String clave, HistorialEvento evento, String tipo) {
        try {
            // La clave (el id del historial) decide la partición: todos los
            // eventos del mismo historial van juntos y en orden.
            kafkaTemplate.send(KafkaConfig.TOPIC_HISTORIALES, clave, evento);
            log.debug("Evento '{}' publicado para el historial {}", tipo, clave);
        } catch (Exception e) {
            // El historial ya está guardado en MongoDB; que el broker esté caído
            // no debe deshacer la operación. Se registra para dejar rastro de
            // que ese evento nunca salió.
            log.error("No se pudo publicar el evento '{}' del historial {}: {}",
                    tipo, clave, e.getMessage(), e);
        }
    }
}
