package com.salud.pacientes.repository;

import com.salud.pacientes.model.HistorialResumen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialResumenRepository extends JpaRepository<HistorialResumen, String> {

    List<HistorialResumen> findByPacienteIdOrderByFechaConsultaDesc(Long pacienteId);

    void deleteByPacienteId(Long pacienteId);
}
