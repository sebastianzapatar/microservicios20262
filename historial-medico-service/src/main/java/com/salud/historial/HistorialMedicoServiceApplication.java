package com.salud.historial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Sigue registrándose en Eureka para que el Gateway pueda enrutar hacia aquí,
 * pero la comunicación con los otros microservicios ya no usa el registro: va
 * por el log de eventos de Kafka (ver {@link com.salud.historial.config.KafkaConfig}).
 * Por eso ya no hace falta el bean de RestClient.Builder que había antes.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class HistorialMedicoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HistorialMedicoServiceApplication.class, args);
    }
}
