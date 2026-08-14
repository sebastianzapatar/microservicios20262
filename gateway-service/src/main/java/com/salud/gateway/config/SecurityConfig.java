package com.salud.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * El Gateway maneja DOS esquemas de autenticación distintos, y por eso hay
 * dos cadenas de filtros separadas:
 *
 *  1. Rutas del microservicio FastAPI: emite su PROPIO JWT (HS256, firmado con
 *     una clave simétrica). Estas rutas NO deben pasar por el filtro de
 *     Keycloak: no basta con marcarlas permitAll, porque el filtro OAuth2 se
 *     ejecuta ante cualquier cabecera "Authorization: Bearer" e intentaría
 *     decodificar el token, fallando con "Unsupported algorithm of HS256"
 *     antes de llegar siquiera a la comprobación de autorización.
 *
 *  2. Todo lo demás: tokens de Keycloak (RS256), validados aquí y de nuevo en
 *     cada microservicio.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Cadena 1 — rutas que NO usan Keycloak. Sin filtro OAuth2: el token viaja
     * intacto hasta FastAPI, que es quien lo valida.
     */
    @Bean
    @Order(1)
    public SecurityWebFilterChain fastapiSecurityWebFilterChain(ServerHttpSecurity http) {
        http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                        "/api/usuarios/**",
                        "/api/consultas/**",
                        "/actuator/**",
                        "/eureka/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll());

        return http.build();
    }

    /**
     * Cadena 2 — todo lo demás exige un JWT válido emitido por Keycloak.
     */
    @Bean
    @Order(2)
    public SecurityWebFilterChain keycloakSecurityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                );

        return http.build();
    }
}
