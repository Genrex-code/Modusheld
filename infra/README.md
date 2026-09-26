# Infraestructura

`docker-compose.yml` crea dos redes:

- `front-network`: cliente y gateway.
- `back-network`: gateway y `demo-api`, marcada como interna.

Solo el gateway publica `8080:8080`. No agregues `ports:` a `demo-api`.

El gateway exige `JWT_SECRET` (32 bytes como minimo), `ADMIN_USERNAME` y
`ADMIN_PASSWORD` desde el archivo `.env`; no existen valores secretos por defecto.

Los Dockerfiles compilan ambos modulos desde un arbol limpio mediante Maven Wrapper y no copian artefactos locales de `target/`.
