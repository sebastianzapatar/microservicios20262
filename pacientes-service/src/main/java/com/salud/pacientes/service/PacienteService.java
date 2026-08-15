package com.salud.pacientes.service;

import com.salud.pacientes.dto.HistorialMedicoResponse;
import com.salud.pacientes.dto.PacienteRequest;
import com.salud.pacientes.dto.PacienteResponse;
import com.salud.pacientes.messaging.PacienteEventoPublisher;
import com.salud.pacientes.model.HistorialResumen;
import com.salud.pacientes.model.Paciente;
import com.salud.pacientes.repository.HistorialResumenRepository;
import com.salud.pacientes.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PacienteRepository pacienteRepository;
    private final HistorialResumenRepository historialResumenRepository;
    private final PacienteEventoPublisher eventoPublisher;

    @Transactional(readOnly = true)
    public List<PacienteResponse> listarTodos() {
        return pacienteRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PacienteResponse obtenerPorId(Long id) {
        Paciente paciente = pacienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Paciente no encontrado con ID: " + id));
        return toResponse(paciente);
    }

    @Transactional(readOnly = true)
    public PacienteResponse obtenerPorDocumento(String numeroDocumento) {
        Paciente paciente = pacienteRepository.findByNumeroDocumento(numeroDocumento)
                .orElseThrow(() -> new RuntimeException("Paciente no encontrado con documento: " + numeroDocumento));
        return toResponse(paciente);
    }

    /**
     * Devuelve el paciente junto con su historial médico, leyendo la tabla local
     * historial_resumen que {@link com.salud.pacientes.messaging.HistorialEventoListener}
     * mantiene al día con los eventos del topic "salud.historiales".
     *
     * CERO llamadas a otros servicios: responde igual aunque
     * historial-medico-service esté completamente caído.
     *
     * Compáralo con la rama de RabbitMQ, donde este mismo endpoint hacía un RPC
     * y devolvía una lista vacía si nadie contestaba. Aquí no hace falta
     * preguntar: el log de Kafka ya entregó todo lo necesario, incluida la
     * historia anterior al primer arranque de este servicio.
     *
     * El precio es la consistencia eventual, y por eso se expone
     * "sincronizadoHasta": deja ver hasta qué momento llegó la réplica.
     */
    @Transactional(readOnly = true)
    public PacienteConHistorialResponse obtenerPacienteConHistorial(Long id) {
        PacienteResponse paciente = obtenerPorId(id);

        List<HistorialResumen> resumenes = historialResumenRepository
                .findByPacienteIdOrderByFechaConsultaDesc(id);

        List<HistorialMedicoResponse> historial = resumenes.stream()
                .map(r -> new HistorialMedicoResponse(
                        r.getId(),
                        r.getPacienteId(),
                        r.getDiagnostico(),
                        r.getTratamiento(),
                        r.getMedico(),
                        r.getFechaConsulta(),
                        r.getNotas(),
                        r.getTipoConsulta()))
                .toList();

        LocalDateTime sincronizadoHasta = resumenes.stream()
                .map(HistorialResumen::getFechaSincronizacion)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new PacienteConHistorialResponse(paciente, historial, sincronizadoHasta);
    }

    @Transactional
    public PacienteResponse crear(PacienteRequest request) {
        if (pacienteRepository.existsByNumeroDocumento(request.numeroDocumento())) {
            throw new RuntimeException("Ya existe un paciente con el documento: " + request.numeroDocumento());
        }
        if (request.email() != null && pacienteRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Ya existe un paciente con el email: " + request.email());
        }

        Paciente paciente = Paciente.builder()
                .nombre(request.nombre())
                .apellido(request.apellido())
                .email(request.email())
                .telefono(request.telefono())
                .fechaNacimiento(request.fechaNacimiento())
                .direccion(request.direccion())
                .tipoDocumento(request.tipoDocumento())
                .numeroDocumento(request.numeroDocumento())
                .build();

        Paciente saved = pacienteRepository.save(paciente);
        eventoPublisher.publicarCreado(saved);
        return toResponse(saved);
    }

    @Transactional
    public PacienteResponse actualizar(Long id, PacienteRequest request) {
        Paciente paciente = pacienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Paciente no encontrado con ID: " + id));

        paciente.setNombre(request.nombre());
        paciente.setApellido(request.apellido());
        paciente.setEmail(request.email());
        paciente.setTelefono(request.telefono());
        paciente.setFechaNacimiento(request.fechaNacimiento());
        paciente.setDireccion(request.direccion());
        paciente.setTipoDocumento(request.tipoDocumento());
        paciente.setNumeroDocumento(request.numeroDocumento());

        Paciente updated = pacienteRepository.save(paciente);
        eventoPublisher.publicarActualizado(updated);
        return toResponse(updated);
    }

    /**
     * Se carga la entidad completa (en lugar de existsById + deleteById) porque
     * el publisher necesita su id para construir la clave del tombstone.
     */
    @Transactional
    public void eliminar(Long id) {
        Paciente paciente = pacienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Paciente no encontrado con ID: " + id));

        pacienteRepository.deleteById(id);

        // Se limpia también la copia local de sus historiales: el dueño real
        // (historial-medico-service) reaccionará al evento por su cuenta.
        historialResumenRepository.deleteByPacienteId(id);

        eventoPublisher.publicarEliminado(paciente);
    }

    private PacienteResponse toResponse(Paciente paciente) {
        return new PacienteResponse(
                paciente.getId(),
                paciente.getNombre(),
                paciente.getApellido(),
                paciente.getEmail(),
                paciente.getTelefono(),
                paciente.getFechaNacimiento(),
                paciente.getDireccion(),
                paciente.getTipoDocumento(),
                paciente.getNumeroDocumento(),
                paciente.getFechaRegistro(),
                paciente.getFechaActualizacion()
        );
    }

    /**
     * Combina los datos del paciente con su historial médico, leído de la
     * réplica local que construyen los eventos de Kafka.
     *
     * "sincronizadoHasta" es null si todavía no llegó ningún evento de historial
     * para ese paciente. Sirve para hacer visible en clase que la réplica va
     * unos milisegundos por detrás del dato original.
     */
    public record PacienteConHistorialResponse(
            PacienteResponse paciente,
            List<HistorialMedicoResponse> historialMedico,
            LocalDateTime sincronizadoHasta
    ) {
    }
}
