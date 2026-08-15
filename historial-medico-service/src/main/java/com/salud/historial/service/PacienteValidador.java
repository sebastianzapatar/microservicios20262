package com.salud.historial.service;

import com.salud.historial.repository.PacienteReplicaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decide si un paciente existe mirando SOLO la réplica local que mantienen los
 * eventos del topic "salud.pacientes".
 *
 * Compara esta clase con su equivalente en la rama de RabbitMQ y se ve de un
 * vistazo por qué Kafka simplifica el diseño. Allí este validador tenía dos
 * caminos: primero la réplica local y, si el paciente no estaba, un RPC de
 * respaldo a pacientes-service. Ese respaldo no era decorativo: la cola
 * arrancaba vacía y los pacientes creados antes de que existiera la cola nunca
 * llegaban, así que sin el RPC se habrían rechazado historiales de pacientes
 * perfectamente válidos.
 *
 * Con Kafka ese problema desaparece. El topic está compactado y se lee desde el
 * offset 0, así que la réplica se construye con TODOS los pacientes que han
 * existido, no solo con los posteriores al arranque. Si un paciente no está en
 * la réplica es porque de verdad no existe (o porque el evento todavía no
 * llegó, que es la consistencia eventual que se paga a cambio).
 */
@Service
@RequiredArgsConstructor
public class PacienteValidador {

    private static final Logger log = LoggerFactory.getLogger(PacienteValidador.class);

    private final PacienteReplicaRepository replicaRepository;

    public boolean existe(Long pacienteId) {
        if (pacienteId == null) {
            return false;
        }

        boolean existe = replicaRepository.existsById(pacienteId);
        log.debug("Paciente {} validado contra la réplica local: {}", pacienteId, existe);
        return existe;
    }
}
