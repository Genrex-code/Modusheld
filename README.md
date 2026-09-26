# ModuShield

ModuShield es un prototipo academico de API Gateway de seguridad construido con Java 17, Spring Boot y Spring Cloud Gateway. El gateway es el unico punto publico: identifica cada solicitud, aplica politicas, devuelve errores uniformes y reenvia solamente trafico permitido a una API interna.

## Estado de integracion

| Area | Estado actual | Responsable |
|---|---|---|
| Estructura Maven y contratos comunes | Integrado | Jairo / Core |
| Routing, request ID y errores 500/502 | Integrado a partir de la entrega A | Java A |
| JWT, usuarios, roles, rutas y metodos | Integrado con pruebas unitarias | Java B |
| Tamano maximo y rate limit | Integrado con pruebas unitarias | Java C |
| Demo API y auditoria | Integrado con pruebas | Java D |
| Docker, E2E y documentacion | Validado en Docker: 12/12 | Jairo |

La matriz E01-E12 fue aprobada en Docker despues de integrar C. Se debe repetir
la corrida y guardar evidencia al preparar el commit o tag definitivo.

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

Antes de iniciar Docker, reemplaza todos los placeholders de `.env`. `JWT_SECRET`
debe tener por lo menos 32 bytes; por ejemplo, puedes generar uno con
`openssl rand -base64 48`. El secreto JWT y la contrasena del administrador son
locales y nunca deben subirse al repositorio.

## Ejecucion con Docker

```bash
docker compose -f infra/docker-compose.yml --env-file .env up -d --build
docker compose -f infra/docker-compose.yml --env-file .env ps
python client-tests/run_demo.py
docker compose -f infra/docker-compose.yml --env-file .env down
```

El host publica unicamente `localhost:8080`. `demo-api:8081` existe solo en `back-network`; si `localhost:8081` responde, el aislamiento esta mal configurado.

## Autenticacion y productos

Registro y login se realizan en el gateway. Los usuarios registrados reciben el
rol `USER`; el usuario `ADMIN` se crea al arrancar a partir de `ADMIN_USERNAME` y
`ADMIN_PASSWORD`. Las contrasenas se conservan como hashes BCrypt y los usuarios
registrados viven en memoria durante la ejecucion del gateway.

```bash
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"reader","password":"change-this-password"}'

curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"reader","password":"change-this-password"}'

curl http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN"
```

Un `USER` puede ejecutar `GET /api/products` y `GET /api/products/{id}`. El
`ADMIN` tambien puede ejecutar `POST /api/products`, `PUT /api/products/{id}` y
`DELETE /api/products/{id}`. Tokens ausentes, invalidos, alterados o vencidos
reciben `401`; una escritura intentada por un `USER` recibe `403`.

## Pruebas durante la integracion

Para ejecutar E01-E09 sin detener contenedores ni inspeccionar redes/logs:

```bash
python client-tests/run_demo.py --http-only
```

La corrida completa ejecuta E01-E12, detiene y vuelve a iniciar `demo-api` durante E10, valida el aislamiento en E11 e inspecciona la auditoria en E12. Cada corrida guarda un JSON sin secretos en `docs/evidence/` y devuelve codigo distinto de cero si existe un fallo. E07 valida la sexta solicitud con 429; E08/E09 validan los bordes de 8192/8193 bytes.

## Contratos que no deben cambiarse sin acuerdo

- Productos: `Authorization: Bearer <JWT>`; ordenes heredadas conservan `X-API-Key`.
- Correlacion: `X-Request-Id` en solicitud reenviada y respuesta.
- Rutas de producto: GET de coleccion/elemento para `USER`; CRUD completo para `ADMIN`.
- Errores JWT: `401 INVALID_TOKEN` y `403 INSUFFICIENT_PERMISSIONS`.
- JSON de error: `timestamp`, `status`, `error`, `message`, `path`, `requestId`.
- Limites: 8192 bytes y cinco solicitudes por diez segundos por identidad.

Consulta [arquitectura](docs/architecture.md), [politicas](docs/policies.md), [estado de integracion](docs/integration.md), [contratos para scripts de presentacion](docs/presentation-contracts.md) y [guion de demostracion](docs/demo-script.md).
