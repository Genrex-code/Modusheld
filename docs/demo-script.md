# Guion de demostracion

Las aserciones y los contratos completos de entrada/salida estan definidos en
[presentation-contracts.md](presentation-contracts.md).

## Preparacion

```bash
git status --short
git describe --tags --always
cp .env.example .env          # solo si aun no existe
./mvnw clean test
docker compose -f infra/docker-compose.yml --env-file .env up -d --build
docker compose -f infra/docker-compose.yml --env-file .env ps
```

Antes de presentar, comprobar que `demo-api` no muestra un puerto del host y que el gateway esta unido a `front-network` y `back-network`.

## Recorrido

1. Mostrar la topologia y explicar que el backend solo existe en la red interna.
2. Ejecutar la matriz completa:

   ```bash
   python client-tests/run_demo.py
   ```

3. Destacar E02 (proxy 200), E03 (401), E05 (403), E06 (405), E07 (429), E09 (413), E10 (502), E11 (aislamiento) y E12 (auditoria sin secreto).
4. Mostrar el resumen `12 passed, 0 failed, 0 skipped` y el archivo generado en `docs/evidence/`.
5. Mostrar una linea de auditoria permitida y una rechazada, ambas correlacionadas por request ID.

## Cierre

```bash
docker compose -f infra/docker-compose.yml --env-file .env logs --no-color gateway
docker compose -f infra/docker-compose.yml --env-file .env down
```

No improvisar cambios durante la exposicion. Si Docker no esta disponible, usar procesos locales y declarar que E10-E12 quedan sin validar; nunca mostrar escenarios omitidos como aprobados.
