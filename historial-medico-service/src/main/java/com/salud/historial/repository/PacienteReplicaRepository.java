package com.salud.historial.repository;

import com.salud.historial.model.PacienteReplica;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PacienteReplicaRepository extends MongoRepository<PacienteReplica, Long> {
}
