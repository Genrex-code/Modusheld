package com.modushield.gateway.policy;

import org.springframework.http.HttpStatus;

/**
 * Contrato compartido definido en el plan maestro (seccion 6.1) y el manual (seccion 5.2).
 *
 * IMPORTANTE: este archivo pertenece formalmente a Java A - Nucleo (paquete config/error).
 * Se incluye aqui como REFERENCIA para que el modulo de Access compile y se pueda probar
 * de forma independiente. Cuando Core publique su version real, usa esa y borra esta copia
 * (o confirma con Jairo que ambas firmas coinciden exactamente).
 */
public record PolicyDecision(
        boolean allowed,
        HttpStatus status,
        String code,
        String rule,
        String safeMessage
) {

    public static PolicyDecision allow() {
        return new PolicyDecision(true, HttpStatus.OK, null, null, null);
    }
}
