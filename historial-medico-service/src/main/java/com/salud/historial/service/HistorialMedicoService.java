package com.salud.historial.service;

import com.salud.historial.dto.HistorialMedicoRequest;
import com.salud.historial.dto.HistorialMedicoResponse;
import com.salud.historial.messaging.HistorialEventoPublisher;
import com.salud.historial.model.HistorialMedico;
import com.salud.historial.repository.HistorialMedicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistorialMedicoService {

    private final HistorialMedicoRepository historialRepository;
    private final PacienteValidador pacienteValidador;
    private final HistorialEventoPublisher eventoPublisher;

    public List<HistorialMedicoResponse> listarTodos() {
        return historialRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public HistorialMedicoResponse obtenerPorId(String id) {
        HistorialMedico historial = historialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Historial médico no encontrado con ID: " + id));
        return toResponse(historial);
    }

    /**
     * Obtiene todos los historiales médicos de un paciente, validando primero
     * que el paciente existe (réplica local y, si no está, RPC por cola).
     */
    public List<HistorialMedicoResponse> obtenerPorPacienteId(Long pacienteId) {
        if (!pacienteValidador.existe(pacienteId)) {
            throw new RuntimeException("Paciente no encontrado con ID: " + pacienteId);
        }

        return listarPorPacienteSinValidar(pacienteId);
    }

    /**
     * Igual que el anterior pero sin validar al paciente. Lo usa
     * {@link com.salud.historial.messaging.HistorialRpcServer} para atender la
     * cola "historial.rpc": quien pregunta es el dueño del dato del paciente, así
     * que volver a validarlo dispararía un RPC de vuelta hacia él.
     */
    public List<HistorialMedicoResponse> listarPorPacienteSinValidar(Long pacienteId) {
        return historialRepository.findByPacienteId(pacienteId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Crea un nuevo historial médico.
     *
     * Valida que el paciente existe contra la réplica local que alimentan los
     * eventos "paciente.*" y, si aún no lo conoce, preguntando por RPC. Al
     * terminar publica "historial.creado" para que los demás servicios se
     * enteren.
     */
    public HistorialMedicoResponse crear(HistorialMedicoRequest request) {
        if (!pacienteValidador.existe(request.pacienteId())) {
            throw new RuntimeException("No se puede crear historial: Paciente no encontrado con ID: " + request.pacienteId());
        }

        HistorialMedico historial = HistorialMedico.builder()
                .pacienteId(request.pacienteId())
                .diagnostico(request.diagnostico())
                .tratamiento(request.tratamiento())
                .medico(request.medico())
                .fechaConsulta(request.fechaConsulta() != null ? request.fechaConsulta() : LocalDateTime.now())
                .notas(request.notas())
                .tipoConsulta(request.tipoConsulta())
                .build();

        HistorialMedico saved = historialRepository.save(historial);
        eventoPublisher.publicarCreado(saved);
        return toResponse(saved);
    }

    public HistorialMedicoResponse actualizar(String id, HistorialMedicoRequest request) {
        HistorialMedico historial = historialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Historial médico no encontrado con ID: " + id));

        historial.setPacienteId(request.pacienteId());
        historial.setDiagnostico(request.diagnostico());
        historial.setTratamiento(request.tratamiento());
        historial.setMedico(request.medico());
        historial.setFechaConsulta(request.fechaConsulta());
        historial.setNotas(request.notas());
        historial.setTipoConsulta(request.tipoConsulta());

        HistorialMedico updated = historialRepository.save(historial);
        eventoPublisher.publicarActualizado(updated);
        return toResponse(updated);
    }

    /**
     * Se carga el documento completo (en lugar de existsById + deleteById) porque
     * el evento "historial.eliminado" viaja con sus datos: quien lo consuma ya no
     * puede ir a buscarlos, porque para entonces el documento ya no está.
     */
    public void eliminar(String id) {
        HistorialMedico historial = historialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Historial médico no encontrado con ID: " + id));

        historialRepository.deleteById(id);
        eventoPublisher.publicarEliminado(historial);
    }

    private HistorialMedicoResponse toResponse(HistorialMedico historial) {
        return new HistorialMedicoResponse(
                historial.getId(),
                historial.getPacienteId(),
                historial.getDiagnostico(),
                historial.getTratamiento(),
                historial.getMedico(),
                historial.getFechaConsulta(),
                historial.getNotas(),
                historial.getTipoConsulta(),
                historial.getFechaCreacion(),
                historial.getFechaActualizacion()
        );
    }
}
