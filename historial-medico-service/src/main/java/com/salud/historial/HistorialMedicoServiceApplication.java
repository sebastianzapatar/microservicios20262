package com.salud.historial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class HistorialMedicoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HistorialMedicoServiceApplication.class, args);
    }
}
