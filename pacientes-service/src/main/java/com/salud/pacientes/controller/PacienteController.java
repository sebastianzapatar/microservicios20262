package com.salud.pacientes.controller;

import com.salud.pacientes.dto.PacienteRequest;
import com.salud.pacientes.dto.PacienteResponse;
import com.salud.pacientes.service.PacienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService pacienteService;

    @GetMapping
    public ResponseEntity<List<PacienteResponse>> listarTodos() {
        return ResponseEntity.ok(pacienteService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PacienteResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pacienteService.obtenerPorId(id));
    }

    @GetMapping("/documento/{numeroDocumento}")
    public ResponseEntity<PacienteResponse> obtenerPorDocumento(@PathVariable String numeroDocumento) {
        return ResponseEntity.ok(pacienteService.obtenerPorDocumento(numeroDocumento));
    }

    /**
     * Endpoint que demuestra la comunicación inter-servicio.
     * Obtiene los datos del paciente junto con su historial médico
     * llamando al microservicio de Historial Médico via HTTP + Eureka.
     */
    @GetMapping("/{id}/historial")
    public ResponseEntity<PacienteService.PacienteConHistorialResponse> obtenerConHistorial(@PathVariable Long id) {
        return ResponseEntity.ok(pacienteService.obtenerPacienteConHistorial(id));
    }

    @PostMapping
    public ResponseEntity<PacienteResponse> crear(@Valid @RequestBody PacienteRequest request) {
        PacienteResponse response = pacienteService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PacienteResponse> actualizar(@PathVariable Long id, @Valid @RequestBody PacienteRequest request) {
        return ResponseEntity.ok(pacienteService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        pacienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
