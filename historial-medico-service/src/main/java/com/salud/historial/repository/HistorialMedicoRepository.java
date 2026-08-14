package com.salud.historial.repository;

import com.salud.historial.model.HistorialMedico;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialMedicoRepository extends MongoRepository<HistorialMedico, String> {

    List<HistorialMedico> findByPacienteId(Long pacienteId);

    List<HistorialMedico> findByMedico(String medico);

    List<HistorialMedico> findByTipoConsulta(String tipoConsulta);

    List<HistorialMedico> findByPacienteIdAndTipoConsulta(Long pacienteId, String tipoConsulta);
}
