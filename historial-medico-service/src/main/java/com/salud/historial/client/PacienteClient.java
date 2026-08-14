package com.salud.historial.client;

import com.salud.historial.dto.PacienteResponse;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Cliente HTTP para comunicarse con el microservicio de Pacientes.
 * Usa DiscoveryClient (Eureka) para resolver la URL del servicio
 * y propaga el token JWT para autenticación inter-servicio.
 */
@Component
public class PacienteClient {

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    public PacienteClient(DiscoveryClient discoveryClient, RestClient.Builder restClientBuilder) {
        this.discoveryClient = discoveryClient;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Verifica si un paciente existe consultando al microservicio
     * de Pacientes via HTTP, resolviendo la URL con Eureka.
     */
    public boolean pacienteExiste(Long pacienteId) {
        String baseUrl = getServiceUrl("PACIENTES-SERVICE");
        if (baseUrl == null) {
            throw new RuntimeException("Servicio de Pacientes no disponible");
        }

        try {
            restClient.get()
                    .uri(baseUrl + "/api/pacientes/{id}", pacienteId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getCurrentJwtToken())
                    .retrieve()
                    .body(PacienteResponse.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtiene los datos de un paciente consultando al microservicio
     * de Pacientes via HTTP + Eureka.
     */
    public PacienteResponse obtenerPaciente(Long pacienteId) {
        String baseUrl = getServiceUrl("PACIENTES-SERVICE");
        if (baseUrl == null) {
            throw new RuntimeException("Servicio de Pacientes no disponible");
        }

        return restClient.get()
                .uri(baseUrl + "/api/pacientes/{id}", pacienteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getCurrentJwtToken())
                .retrieve()
                .body(PacienteResponse.class);
    }

    /**
     * Resuelve la URL del servicio usando Eureka DiscoveryClient.
     */
    private String getServiceUrl(String serviceName) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances.isEmpty()) {
            return null;
        }
        ServiceInstance instance = instances.getFirst();
        return instance.getUri().toString();
    }

    /**
     * Extrae el JWT del contexto de seguridad actual para propagarlo
     * en las peticiones inter-servicio.
     */
    private String getCurrentJwtToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        return "";
    }
}
