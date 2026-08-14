package com.salud.pacientes.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Traduce las excepciones del servicio a códigos HTTP con sentido.
 * Sin esto, un "paciente no encontrado" devolvería 500 en vez de 404.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        String mensaje = ex.getMessage() != null ? ex.getMessage() : "Error inesperado";
        // "no disponible" va PRIMERO y se traduce a 503: un servicio que no
        // contesta por la cola no es lo mismo que un dato que no existe, y
        // devolver 404 en ese caso haría creer al cliente que puede dejar de
        // reintentar. Mismo criterio que en historial-medico-service.
        HttpStatus status = mensaje.contains("no disponible")
                ? HttpStatus.SERVICE_UNAVAILABLE
                : mensaje.contains("Ya existe")
                ? HttpStatus.CONFLICT
                : mensaje.contains("no encontrado") ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(body(status, mensaje));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> respuesta = body(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos");
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.put(error.getField(), error.getDefaultMessage()));
        respuesta.put("errores", errores);
        return ResponseEntity.badRequest().body(respuesta);
    }

    private Map<String, Object> body(HttpStatus status, String mensaje) {
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("timestamp", LocalDateTime.now().toString());
        respuesta.put("status", status.value());
        respuesta.put("error", status.getReasonPhrase());
        respuesta.put("mensaje", mensaje);
        return respuesta;
    }
}
