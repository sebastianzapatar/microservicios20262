package com.salud.historial.controller;

import com.salud.historial.dto.HistorialMedicoRequest;
import com.salud.historial.dto.HistorialMedicoResponse;
import com.salud.historial.service.HistorialMedicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/historiales")
@RequiredArgsConstructor
public class HistorialMedicoController {

    private final HistorialMedicoService historialService;

    @GetMapping
    public ResponseEntity<List<HistorialMedicoResponse>> listarTodos() {
        return ResponseEntity.ok(historialService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HistorialMedicoResponse> obtenerPorId(@PathVariable String id) {
        return ResponseEntity.ok(historialService.obtenerPorId(id));
    }

    /**
     * Endpoint que demuestra la comunicación inter-servicio.
     * Obtiene los historiales de un paciente, validando primero que
     * el paciente existe llamando al microservicio de Pacientes via HTTP + Eureka.
     */
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<HistorialMedicoResponse>> obtenerPorPaciente(@PathVariable Long pacienteId) {
        return ResponseEntity.ok(historialService.obtenerPorPacienteId(pacienteId));
    }

    /**
     * Crea un historial médico, validando la existencia del paciente
     * en el otro microservicio antes de persistir.
     */
    @PostMapping
    public ResponseEntity<HistorialMedicoResponse> crear(@Valid @RequestBody HistorialMedicoRequest request) {
        HistorialMedicoResponse response = historialService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<HistorialMedicoResponse> actualizar(@PathVariable String id,
                                                               @Valid @RequestBody HistorialMedicoRequest request) {
        return ResponseEntity.ok(historialService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        historialService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
