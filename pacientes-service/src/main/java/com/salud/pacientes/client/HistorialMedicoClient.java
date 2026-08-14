package com.salud.pacientes.client;

import com.salud.pacientes.dto.HistorialMedicoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

/**
 * Cliente HTTP para comunicarse con el microservicio de Historial Médico.
 * Usa DiscoveryClient (Eureka) para resolver la URL del servicio
 * y propaga el token JWT para autenticación inter-servicio.
 */
@Component
public class HistorialMedicoClient {

    private static final Logger log = LoggerFactory.getLogger(HistorialMedicoClient.class);

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    public HistorialMedicoClient(DiscoveryClient discoveryClient, RestClient.Builder restClientBuilder) {
        this.discoveryClient = discoveryClient;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Obtiene el historial médico de un paciente consultando al microservicio
     * de Historial Médico via HTTP, resolviendo la URL con Eureka.
     */
    public List<HistorialMedicoResponse> obtenerHistorialPorPaciente(Long pacienteId) {
        String baseUrl = getServiceUrl("HISTORIAL-MEDICO-SERVICE");
        if (baseUrl == null) {
            log.warn("HISTORIAL-MEDICO-SERVICE no está registrado en Eureka; se devuelve historial vacío");
            return Collections.emptyList();
        }

        try {
            List<HistorialMedicoResponse> response = restClient.get()
                    .uri(baseUrl + "/api/historiales/paciente/{pacienteId}", pacienteId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getCurrentJwtToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return response != null ? response : Collections.emptyList();
        } catch (Exception e) {
            // Degradación elegante: si el otro servicio falla, el paciente se
            // devuelve igualmente pero sin historial. Se registra el motivo:
            // un catch silencioso convierte un error en "no hay datos".
            log.error("Fallo al consultar el historial del paciente {} en {}: {}",
                    pacienteId, baseUrl, e.getMessage(), e);
            return Collections.emptyList();
        }
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
