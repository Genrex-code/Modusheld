# Contratos de entrada y salida para pruebas de presentacion

Este documento es la fuente de trabajo para los scripts de demostracion de
ModuShield. Describe el contrato final esperado; no sustituye el resultado de
una ejecucion real.

> Estado al actualizar este reporte: A, B, C, D y Core estan integrados. La
> matriz completa fue ejecutada en Docker con `12 passed, 0 failed, 0 skipped`.

## 1. Punto de entrada y configuracion

- URL publica: `http://localhost:8080`.
- El backend `demo-api:8081` solo existe dentro de `back-network`; nunca debe
  responder en `http://localhost:8081` desde el host.
- Todas las cargas y respuestas de negocio usan JSON UTF-8.
- La credencial se envia en `X-API-Key` y se obtiene de
  `MODUSHIELD_API_KEY`. El script no debe imprimirla ni guardarla en evidencia.
- `X-Request-Id` es opcional. Si se envia, debe cumplir
  `[A-Za-z0-9][A-Za-z0-9._:-]{0,127}`; el gateway conserva un valor valido y
  reemplaza uno ausente o inseguro por un UUID.

Valores de prueba por defecto:

| Variable | Valor |
|---|---:|
| `MAX_REQUEST_SIZE_BYTES` | `8192` |
| `RATE_LIMIT_CAPACITY` | `5` |
| `RATE_LIMIT_WINDOW_SECONDS` | `10` |

## 2. Contratos HTTP de entrada

| Metodo y ruta | Headers | Cuerpo | Resultado nominal |
|---|---|---|---|
| `GET /health` | Ninguno obligatorio | Sin cuerpo | `200` |
| `GET /api/products` | Un solo `X-API-Key` valido | Sin cuerpo | `200` |
| `POST /api/orders` | Un solo `X-API-Key` valido y `Content-Type: application/json` | `productId` no vacio y `quantity` entre 1 y 100 | `201` |

Entrada minima para crear una orden:

```json
{
  "productId": "P-100",
  "quantity": 2
}
```

Reglas negativas:

- `/api/admin/status` existe en el backend, pero el gateway siempre lo niega
  con `403 ROUTE_NOT_ALLOWED`.
- Una ruta fuera de la allowlist se niega con `403 ROUTE_NOT_ALLOWED`.
- Una ruta conocida con un metodo no permitido se niega con
  `405 METHOD_NOT_ALLOWED`.
- Una ruta protegida sin exactamente un `X-API-Key` valido se niega con
  `401 INVALID_API_KEY`.
- Para `POST`, `PUT` y `PATCH`, un `Content-Length` ausente, invalido, negativo
  o repetido se niega con `413 PAYLOAD_TOO_LARGE`. Tambien se niega cualquier
  `Transfer-Encoding` porque el tamano no puede comprobarse antes de aceptar.
- Una carga de exactamente 8192 bytes se permite; una de 8193 bytes se niega.
  Los fixtures oficiales son `client-tests/payload-8192.json` y
  `client-tests/payload-8193.json`.
- La tasa es una ventana fija por identidad: las primeras cinco solicitudes
  se permiten y la sexta se niega con `429 RATE_LIMIT_EXCEEDED`. Con una API
  key valida, esa clave identifica el bucket, pero solo se conserva su hash.

Para evitar resultados contaminados, el script debe esperar 11 segundos antes
de E07 y otros 11 segundos antes de E08. Cada prueba negativa debe introducir
un solo error deliberado.

## 3. Contratos HTTP de salida exitosa

### Salud

`GET /health` devuelve `200`:

```json
{
  "status": "UP"
}
```

Este endpoint de gestion no requiere `X-API-Key`. El guion no debe exigir
`X-Request-Id` en E01.

### Productos

`GET /api/products` devuelve `200` y este arreglo:

```json
[
  {"id": "P-100", "name": "Demo product", "stock": 12},
  {"id": "P-200", "name": "Sample item", "stock": 7}
]
```

El script debe validar que la raiz sea un arreglo y que cada producto tenga
`id` y `name` de tipo string y `stock` entero. Para la demo actual tambien
puede comparar los dos elementos exactos.

### Orden creada

`POST /api/orders` devuelve `201`:

```json
{
  "orderId": "ORD-a1b2c3d4",
  "productId": "P-100",
  "quantity": 2,
  "status": "SIMULATED"
}
```

`orderId` es dinamico y se valida con `^ORD-[0-9a-f]{8}$`; no debe compararse
contra el valor del ejemplo. `productId` y `quantity` deben coincidir con la
entrada y `status` debe ser `SIMULATED`.

El `400` producido por datos de orden invalidos pertenece al backend y no forma
parte de la matriz E01-E12 ni del JSON uniforme de rechazo del gateway.

## 4. Contrato uniforme de rechazo

Todo rechazo creado por el gateway usa `Content-Type: application/json`, el
status HTTP correspondiente y exactamente estos campos publicos:

```json
{
  "timestamp": "2026-09-24T18:30:00Z",
  "status": 429,
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Request limit exceeded",
  "path": "/api/products",
  "requestId": "e07-demo-001"
}
```

Asersiones obligatorias del script:

1. `timestamp` es un string ISO-8601 UTC parseable.
2. `status` es numerico e igual al status HTTP.
3. `error` coincide exactamente con la matriz siguiente.
4. `message` es un string seguro y no vacio.
5. `path` coincide con la ruta solicitada.
6. `requestId` es no vacio e igual al header de respuesta `X-Request-Id`.
7. No aparecen `rule`, API keys, headers de autorizacion, cuerpo de entrada,
   excepciones ni stack traces.

| HTTP | `error` | `message` actual | Regla interna, no publica |
|---:|---|---|---|
| 401 | `INVALID_API_KEY` | `Missing or invalid API key` | `API_KEY` |
| 403 | `ROUTE_NOT_ALLOWED` | `Route is not permitted` | `ROUTE` |
| 405 | `METHOD_NOT_ALLOWED` | `Method is not permitted for this route` | `METHOD` |
| 413 | `PAYLOAD_TOO_LARGE` | `Request payload exceeds the maximum allowed size or cannot be safely measured` | `SIZE` |
| 429 | `RATE_LIMIT_EXCEEDED` | `Request limit exceeded` | `RATE_LIMIT` |
| 502 | `UPSTREAM_UNAVAILABLE` | `Upstream service unavailable` | `UPSTREAM` |
| 500 | `INTERNAL_GATEWAY_ERROR` | `Unexpected gateway error` | `INTERNAL` |

Para que los tests resistan cambios editoriales futuros, el criterio de paso
debe fijarse en HTTP + `error`; el texto exacto de `message` puede registrarse
sin usarlo como identificador de maquina.

## 5. Matriz que debe automatizar la presentacion

| ID | Preparacion y entrada | Salida que aprueba |
|---|---|---|
| E01 | `GET /health` | `200`, `status=UP` |
| E02 | `GET /api/products` con clave valida | `200`, arreglo de dos productos |
| E03 | `GET /api/products` sin clave | `401 INVALID_API_KEY` |
| E04 | `GET /api/products` con clave incorrecta | `401 INVALID_API_KEY` |
| E05 | `GET /api/admin/status` con clave valida | `403 ROUTE_NOT_ALLOWED` |
| E06 | `DELETE /api/products` con clave valida | `405 METHOD_NOT_ALLOWED` |
| E07 | Tras 11 s, seis `GET /api/products` consecutivos con la misma clave | statuses exactos `[200, 200, 200, 200, 200, 429]`; el ultimo `RATE_LIMIT_EXCEEDED` |
| E08 | Tras otros 11 s, `POST /api/orders` con el fixture de 8192 bytes | `201`, orden con contrato valido |
| E09 | `POST /api/orders` con el fixture de 8193 bytes | `413 PAYLOAD_TOO_LARGE` |
| E10 | Detener `demo-api`, pedir productos y reiniciarlo en `finally` | `502 UPSTREAM_UNAVAILABLE` |
| E11 | Intentar `localhost:8081` y `demo-api:8081` desde `front-network` | Ambos intentos fallan; el backend sigue disponible desde `back-network` |
| E12 | Enviar un `X-Request-Id` unico y consultar logs del gateway | `200`; ID presente en respuesta/log y API key ausente del log |

E10 y E11 modifican temporalmente el entorno. Deben ejecutarse contra el stack
de demostracion, nunca contra un ambiente compartido. E10 siempre debe volver a
iniciar `demo-api`, incluso si la asercion falla.

## 6. Contrato interno para integrar politicas

Las politicas no construyen JSON. Todas implementan:

```java
PolicyDecision evaluate(ServerWebExchange exchange);
```

Y devuelven el record comun:

```java
PolicyDecision(
    boolean allowed,
    HttpStatus status,
    String code,
    String rule,
    String safeMessage
)
```

Una decision permitida usa `PolicyDecision.allow()`. Una denegada se entrega a
`ErrorResponseWriter.write(exchange, decision)`. `JsonErrorResponseWriter`,
propiedad de Core, es el unico componente que transforma la decision al JSON
publico descrito arriba.

La carpeta aislada `C/` conserva espejos de compilacion con los mismos paquetes
y firmas para comprobar compatibilidad. El runtime usa las politicas integradas
en `gateway-service/policy/limits` y los contratos canonicos de Core; no carga
las copias de staging.

## 7. Ejecucion y evidencia

El ejecutor base ya disponible es:

```powershell
python client-tests/run_demo.py
```

Debe terminar con codigo `0`, mostrar `12 passed, 0 failed, 0 skipped` y crear
`docs/evidence/e2e-<fecha-UTC>.json`. La evidencia puede contener IDs, estados y
codigos de error, pero nunca la API key ni cuerpos de solicitudes.

El resultado observado despues de integrar C es 12/12. La corrida debe repetirse
sobre el commit o tag usado en la presentacion para generar la evidencia final.
