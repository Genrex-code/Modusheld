# ModuShield

ModuShield es un prototipo academico de API Gateway de seguridad construido con Java 17, Spring Boot y Spring Cloud Gateway. El gateway es el unico punto publico: identifica cada solicitud, aplica politicas, devuelve errores uniformes y reenvia solamente trafico permitido a una API interna.

## Estado de integracion

| Area | Estado actual | Responsable |
|---|---|---|
| Estructura Maven y contratos comunes | Integrado | Jairo / Core |
| Routing, request ID y errores 500/502 | Integrado a partir de la entrega A | Java A |
| API key, rutas y metodos | Integrado con pruebas unitarias | Java B |
| Tamano maximo y rate limit | Pendiente de integrar | Java C |
| Demo API y auditoria | Integrado con pruebas | Java D |
| Docker, E2E y documentacion | Validado en Docker: 10/12; E07 y E09 esperan C | Jairo |

No se debe marcar E01-E12 como aprobado hasta integrar C y guardar una ejecucion real.

## Requisitos

- Java 17 o superior para ejecutar Maven con objetivo Java 17.
- Docker con Docker Compose para la topologia completa.
- Python 3 para el ejecutor E2E (solo usa la biblioteca estandar).

No hace falta instalar Maven globalmente: el repositorio incluye Maven Wrapper.

## Preparacion

```bash
cp .env.example .env
./mvnw clean test
```

En Windows PowerShell:

```powershell
Copy-Item .env.example .env
.\mvnw.cmd clean test
```

La clave de `.env` es local y nunca debe subirse al repositorio.

## Ejecucion con Docker

```bash
docker compose -f infra/docker-compose.yml --env-file .env up -d --build
docker compose -f infra/docker-compose.yml --env-file .env ps
python client-tests/run_demo.py
docker compose -f infra/docker-compose.yml --env-file .env down
```

El host publica unicamente `localhost:8080`. `demo-api:8081` existe solo en `back-network`; si `localhost:8081` responde, el aislamiento esta mal configurado.

## Pruebas durante la integracion

Para ejecutar E01-E09 sin detener contenedores ni inspeccionar redes/logs:

```bash
python client-tests/run_demo.py --http-only
```

La corrida completa ejecuta E01-E12, detiene y vuelve a iniciar `demo-api` durante E10, valida el aislamiento en E11 e inspecciona la auditoria en E12. Cada corrida guarda un JSON sin secretos en `docs/evidence/` y devuelve codigo distinto de cero si existe un fallo. E07 y E09 quedaran aprobados al integrar Limits (C); E08 ya valida el borde permitido de 8192 bytes.

## Contratos que no deben cambiarse sin acuerdo

- Header de identidad: `X-API-Key`.
- Correlacion: `X-Request-Id` en solicitud reenviada y respuesta.
- Rutas permitidas: `GET /health`, `GET /api/products`, `POST /api/orders`.
- Errores: `401 INVALID_API_KEY`, `403 ROUTE_NOT_ALLOWED`, `405 METHOD_NOT_ALLOWED`, `413 PAYLOAD_TOO_LARGE`, `429 RATE_LIMIT_EXCEEDED`, `502 UPSTREAM_UNAVAILABLE` y `500 INTERNAL_GATEWAY_ERROR`.
- JSON de error: `timestamp`, `status`, `error`, `message`, `path`, `requestId`.
- Limites: 8192 bytes y cinco solicitudes por diez segundos por identidad.

Consulta [arquitectura](docs/architecture.md), [politicas](docs/policies.md), [estado de integracion](docs/integration.md) y [guion de demostracion](docs/demo-script.md).
