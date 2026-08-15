package com.salud.pacientes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Copia local de solo lectura ("read model") de los historiales médicos,
 * construida a partir de los eventos del topic "salud.historiales".
 *
 * No es la fuente de la verdad: el dueño del dato sigue siendo
 * historial-medico-service con su MongoDB. Esta tabla existe para que
 * pacientes-service pueda responder sin llamar a nadie, aunque el otro
 * microservicio esté caído.
 *
 * Frente a la rama de RabbitMQ hay una diferencia importante: si esta tabla se
 * borra, basta con reiniciar el consumer group desde el offset 0 y Kafka
 * reproduce todo el log para reconstruirla. Con una cola eso no era posible,
 * porque los mensajes ya consumidos habían desaparecido del broker.
 *
 * El id es el _id de MongoDB del historial, así que reprocesar un evento
 * sobrescribe la fila en lugar de duplicarla: la operación es idempotente, algo
 * imprescindible porque Kafka garantiza "al menos una vez", no "exactamente una vez".
 */
@Entity
@Table(name = "historial_resumen", indexes = @Index(name = "idx_resumen_paciente", columnList = "paciente_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialResumen {

    @Id
    private String id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    private String diagnostico;

    private String tratamiento;

    private String medico;

    @Column(name = "fecha_consulta")
    private LocalDateTime fechaConsulta;

    @Column(length = 2000)
    private String notas;

    @Column(name = "tipo_consulta")
    private String tipoConsulta;

    /** Cuándo se aplicó aquí el evento. Útil para ver el retraso de la réplica. */
    @Column(name = "fecha_sincronizacion")
    private LocalDateTime fechaSincronizacion;
}
