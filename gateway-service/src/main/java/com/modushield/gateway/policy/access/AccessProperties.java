package com.modushield.gateway.policy.access;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuracion del modulo de acceso.
 * Prefijo modushield.access segun la regla de la seccion 6.2 del plan maestro
 * (cada modulo usa su propio prefijo para evitar conflictos).
 *
 * En application.yml:
 *
 * modushield:
 *   access:
 *     api-key: ${MODUSHIELD_API_KEY:}
 */
@ConfigurationProperties(prefix = "modushield.access")
public class AccessProperties {

    /** Valor esperado de la cabecera X-API-Key. Cargado desde MODUSHIELD_API_KEY. */
    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
