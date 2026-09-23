# Arquitectura de ModuShield

## Topologia

```text
Cliente / pruebas E2E
          |
          | HTTP :8080 (front-network)
          v
  ModuShield Gateway
  - requestId
  - auditoria
  - ruta y metodo
  - API key
  - tamano y tasa
          |
          | HTTP :8081 (back-network interna)
          v
       demo-api
   sin puerto en el host
```

El gateway es el unico servicio unido a ambas redes. `demo-api` usa `expose: 8081` como documentacion interna, pero no tiene `ports:` y no puede llamarse desde el host ni desde `front-network`.

## Flujo congelado

1. `RequestIdFilter` conserva o crea `X-Request-Id`.
2. `AuditFilter` de Java D abre el contexto de auditoria.
3. `RouteMethodPolicy` valida la allowlist y el metodo.
4. `ApiKeyFilter` valida `X-API-Key` sin registrarla.
5. `RequestSizePolicy` aplica el limite de 8192 bytes.
6. `RateLimitPolicy` aplica cinco solicitudes por diez segundos por identidad.
7. Spring Cloud Gateway reenvia a `${DEMO_API_URL}`.
8. `AuditFilter` emite una sola decision con estado y latencia.

El plan maestro prevalece sobre el manual operativo cuando difieren; por eso ruta/metodo se evalua antes que la API key.

Orden numerico de integracion:

| Componente | Orden |
|---|---:|
| `RequestIdFilter` | -100 |
| `AuditFilter` (pendiente de D) | -90 |
| `GatewayErrorFilter` | -80 |
| `RouteMethodPolicy` | 30 |
| `ApiKeyFilter` | 40 |
| `RequestSizePolicy` (pendiente de C) | 50 |
| `RateLimitPolicy` (pendiente de C) | 60 |

La auditoria envuelve el manejo de errores para observar el estado final 500/502, y ambos envuelven las politicas que pueden terminar la cadena.

## Componentes integrados

- `GatewayApplication`: bootstrap reactivo y descubrimiento de propiedades.
- `RequestIdFilter`: correlacion segura en request y response.
- `GatewayErrorFilter`: transforma fallos del upstream en 502 y fallos imprevistos en 500.
- `JsonErrorResponseWriter`: unico serializador del contrato de error.
- `ApiKeyFilter` y `RouteMethodPolicy`: decisiones de acceso independientes.
- `AuditFilter`: evento unico y sanitizado para respuestas permitidas, denegadas y errores.
- `demo-api`: productos, orden simulada, health y endpoint administrativo interno.

Los componentes de limites se conectaran cuando llegue la entrega C. No se agregan implementaciones provisionales dentro de paquetes ajenos.
