package com.salud.pacientes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Sigue registrándose en Eureka para que el Gateway pueda enrutar hacia aquí,
 * pero la comunicación con los otros microservicios ya no usa el registro: va
 * por RabbitMQ (ver {@link com.salud.pacientes.config.RabbitMQConfig}). Por eso
 * ya no hace falta el bean de RestClient.Builder que había antes.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class PacientesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PacientesServiceApplication.class, args);
    }
}
