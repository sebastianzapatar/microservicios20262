package com.salud.historial.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Copia local de solo lectura de los pacientes, construida a partir de los
 * eventos "paciente.*" que llegan por RabbitMQ.
 *
 * No es la fuente de la verdad: el dueño del dato sigue siendo pacientes-service
 * con su PostgreSQL. Existe para que este servicio pueda validar "¿este paciente
 * existe?" mirando su propia base, sin llamar a nadie.
 *
 * El id es el mismo del paciente en PostgreSQL, así que reprocesar un evento
 * sobrescribe el documento en lugar de duplicarlo.
 */
@Document(collection = "pacientes_replica")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PacienteReplica {

    @Id
    private Long id;

    private String nombre;

    private String apellido;

    private String email;

    private String telefono;

    private String direccion;

    private String tipoDocumento;

    private String numeroDocumento;

    /** Cuándo se aplicó aquí el evento. Útil para ver el retraso de la réplica. */
    private LocalDateTime fechaSincronizacion;
}
