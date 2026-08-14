package com.salud.pacientes.messaging;

import com.salud.pacientes.config.RabbitMQConfig;
import com.salud.pacientes.dto.evento.HistorialEvento;
import com.salud.pacientes.model.HistorialResumen;
import com.salud.pacientes.repository.HistorialResumenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consume los eventos "historial.*" y mantiene con ellos la tabla
 * historial_resumen (ver {@link HistorialResumen}).
 *
 * Esto es lo que sustituye a la llamada HTTP que antes hacía este servicio:
 * en vez de preguntar "¿qué historiales tiene el paciente 1?" cuando le
 * consultan, se le va contando cada cambio a medida que ocurre.
 */
@Component
@RequiredArgsConstructor
public class HistorialEventoListener {

    private static final Logger log = LoggerFactory.getLogger(HistorialEventoListener.class);

    private final HistorialResumenRepository resumenRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_EVENTOS_HISTORIAL)
    @Transactional
    public void recibir(HistorialEvento evento) {
        if (evento.historialId() == null) {
            log.warn("Evento de historial sin historialId, se descarta: {}", evento);
            return;
        }

        log.debug("Evento '{}' recibido para el historial {}", evento.tipo(), evento.historialId());

        // El "tipo" viene en el cuerpo del mensaje y repite la routing key.
        switch (String.valueOf(evento.tipo())) {
            case "historial.creado", "historial.actualizado" -> guardar(evento);
            case "historial.eliminado" -> resumenRepository.deleteById(evento.historialId());
            default -> log.warn("Tipo de evento de historial no reconocido: {}", evento.tipo());
        }
    }

    /**
     * save() sobre un id que ya existe actualiza la fila, así que reprocesar el
     * mismo evento dos veces deja el mismo resultado. Importa porque RabbitMQ
     * garantiza "al menos una vez", no "exactamente una vez".
     */
    private void guardar(HistorialEvento evento) {
        resumenRepository.save(HistorialResumen.builder()
                .id(evento.historialId())
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
