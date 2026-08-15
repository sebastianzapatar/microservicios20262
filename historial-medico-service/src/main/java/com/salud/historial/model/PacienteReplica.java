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
 * eventos del topic "salud.pacientes".
 *
 * No es la fuente de la verdad: el dueño del dato sigue siendo pacientes-service
 * con su PostgreSQL. Existe para que este servicio pueda validar "¿este paciente
 * existe?" mirando su propia base, sin llamar a nadie.
 *
 * Aquí está la diferencia práctica más grande con la rama de RabbitMQ. Allí esta
 * réplica arrancaba VACÍA: la cola solo traía lo que se publicara a partir de
 * su creación, así que los pacientes anteriores no estaban y hacía falta un RPC
 * de respaldo para preguntarlos. En Kafka el log está entero y compactado, así
 * que al arrancar por primera vez este servicio lee desde el offset 0 y se
 * construye la réplica COMPLETA. Por eso esta rama no tiene RPC.
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
