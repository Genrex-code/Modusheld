# Estado y reglas de integracion

## Lo integrado

- La entrega A fue trasladada desde su carpeta separada al paquete comun `com.modushield.gateway`.
- La entrega B conserva sus clases y pruebas de acceso.
- La entrega D aporta `demo-api` y la auditoria reactiva del gateway.
- El contrato `ErrorResponseWriter` de B tiene una implementacion unica, `JsonErrorResponseWriter`, compatible con `PolicyDecision`.
- El POM raiz fija Java 17, Spring Boot 3.4.5 y Spring Cloud 2024.0.1.
- Las politicas de C estan integradas como filtros con orden 50 para tamano y 60 para tasa.
- Infraestructura y E2E fueron validados con Docker; pasan los 12 escenarios obligatorios.

## Ajustes hechos al integrar A

- Se elimino el texto invalido que aparecia despues del XML del POM original.
- Se unificaron los paquetes `modushield.*` y `com.modushield.gateway.*`.
- `DEMO_API_URL` sustituyo a `http://localhost:8081` fijo.
- Se retiro `StripPrefix=1`; el backend recibe las rutas `/api/**` acordadas.
- El campo publico `code` se cambio por `error` y se agrego `path` al JSON.
- Los errores imprevistos se convierten a `500 INTERNAL_GATEWAY_ERROR` sin filtrar detalles.

La carpeta original de A no fue modificada y `target/` no se copio.

## Integracion de C

C quedo integrada bajo:

```text
gateway-service/src/main/java/com/modushield/gateway/policy/limits/
gateway-service/src/test/java/com/modushield/gateway/policy/limits/
```

Ambas politicas implementan `GatewayPolicy`, `GlobalFilter` y `Ordered`. Usan el
`ErrorResponseWriter` y `PolicyDecision` compartidos, propiedades con prefijo
`modushield.limits`, orden 50 para tamano, orden 60 para tasa y pruebas
deterministas para 8192/8193 bytes y solicitudes 1-6.

## Ajustes hechos al integrar D

- Se alineo Spring Boot 3.3.5 con la version comun 3.4.5 del reactor.
- Se retiro `DemoApiKeyFilter`: la autenticacion pertenece al gateway y la clave fija de la entrega no coincidia con la configuracion compartida.
- `/api/admin/status` devuelve 200 dentro de la red privada; el gateway demuestra el bloqueo devolviendo 403 antes de reenviar.
- El filtro servlet de auditoria se sustituyo por un `GlobalFilter` reactivo en `gateway-service` con orden -90.
- La auditoria envuelve errores y politicas, registra ALLOW/DENY/ERROR y no incluye la API key ni el cuerpo.
- Los endpoints conservan el prefijo `/api` y `demo-api` no publica un puerto al host.

## Revision de cada entrega

1. Confirmar que solo toca sus paquetes y pruebas.
2. Leer pruebas y contrato antes de la implementacion.
3. Ejecutar `./mvnw clean test` y una prueba negativa adicional.
4. Buscar `.env`, secretos, `target/`, logs y binarios.
5. Integrar una pieza y repetir la regresion antes de la siguiente.
