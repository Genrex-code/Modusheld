# Politicas y contrato HTTP

## Endpoints de demostracion

| Metodo | Ruta | Resultado |
|---|---|---|
| GET | `/health` | 200 sin autenticacion |
| POST | `/auth/register` | 201 y usuario con rol USER |
| POST | `/auth/login` | 200 y JWT firmado |
| GET | `/api/products[/{id}]` | USER o ADMIN con JWT valido |
| POST | `/api/products` | Solo ADMIN |
| PUT/DELETE | `/api/products/{id}` | Solo ADMIN |
| POST | `/api/orders` | 201 con API key y cuerpo aceptado |
| GET | `/api/admin/status` | 403 desde el gateway aunque exista en el backend |

## Matriz inmutable de errores

| HTTP | `error` | `rule` interna | Responsable |
|---:|---|---|---|
| 401 | `INVALID_TOKEN` | `AUTHENTICATION` | Auth |
| 403 | `INSUFFICIENT_PERMISSIONS` | `AUTHORIZATION` | Auth |
| 401 | `INVALID_API_KEY` | `API_KEY` | Access |
| 403 | `ROUTE_NOT_ALLOWED` | `ROUTE` | Access |
| 405 | `METHOD_NOT_ALLOWED` | `METHOD` | Access |
| 413 | `PAYLOAD_TOO_LARGE` | `SIZE` | Limits |
| 429 | `RATE_LIMIT_EXCEEDED` | `RATE_LIMIT` | Limits |
| 502 | `UPSTREAM_UNAVAILABLE` | `UPSTREAM` | Core |
| 500 | `INTERNAL_GATEWAY_ERROR` | `INTERNAL` | Core |

Respuesta publica:

```json
{
  "timestamp": "2026-09-24T18:30:00Z",
  "status": 429,
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Request limit exceeded",
  "path": "/api/products",
  "requestId": "6fe8dc2f"
}
```

Cada politica crea un `PolicyDecision`; ninguna escribe su propio JSON. El cuerpo no incluye `rule`, API keys, headers de autorizacion, stack traces ni cuerpos de solicitudes.

## Configuracion

| Variable | Ejemplo | Consumidor |
|---|---|---|
| `MODUSHIELD_API_KEY` | `demo-key-change-me` | Access |
| `JWT_SECRET` | placeholder de 32+ bytes | Auth |
| `JWT_EXPIRATION_SECONDS` | `3600` | Auth |
| `ADMIN_USERNAME` | placeholder local | Auth |
| `ADMIN_PASSWORD` | placeholder local | Auth |
| `DEMO_API_URL` | `http://demo-api:8081` | Core |
| `MAX_REQUEST_SIZE_BYTES` | `8192` | Limits |
| `RATE_LIMIT_CAPACITY` | `5` | Limits |
| `RATE_LIMIT_WINDOW_SECONDS` | `10` | Limits |
| `SERVER_PORT` | `8080` / `8081` | Ambos servicios |

Los valores locales viven en `.env`. Solo `.env.example` pertenece al repositorio.
