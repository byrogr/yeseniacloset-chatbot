# Fase 1.1 — Contrato OpenAPI único

## Objetivo

Reemplazar los contratos por endpoint por un único `src/main/resources/openapi/openapi.yml` y alinear el endpoint de la Fase 1 con las guías de diseño de API de `CLAUDE.md`. Es un refactor: el comportamiento del dominio no cambia.

## Contexto

- El contrato completo ya está en el repo: `src/main/resources/openapi/openapi.yml`. Define las operaciones de las Fases 1, 2 y 3. Esta fase solo **implementa** `getCustomerAccountStatus`; las demás se implementan en su fase.
- El lint está configurado en `.spectral.yaml`.
- No modifiques `openapi.yml` en esta fase. Si encuentras algo que impide generar o implementar, detente y avísame con la propuesta de cambio.

## Alcance

**Incluye**

1. Eliminar los contratos OpenAPI anteriores y todo el código generado o escrito a mano a partir de ellos (DTOs e interfaces), y reemplazar `ops.web.AccountStatusResource` por `ops.web.CustomerResource`.
2. Configurar el generador que ya usa el proyecto para que lea **solo** `openapi.yml`.
3. Configurar la ruta base.
4. Implementar `getCustomerAccountStatus` con los DTOs generados.
5. Agregar el manejo de errores con Problem Details.
6. Actualizar los tests.

**No incluye**: implementar las operaciones de `Conversations` ni de `Webhooks`, ni cambiar la lógica de `sheets` o `account`.

## 1. Generación de código

- Un solo archivo de entrada: `src/main/resources/openapi/openapi.yml`.
- Solo interfaces y modelos (sin implementación generada). Paquetes:
  - Interfaces: `pe.rmsolutions.chatbot.api`
  - Modelos: `pe.rmsolutions.chatbot.api.model`
  - Si el generador deja el código en `target/generated-sources`, no se versiona; si lo deja en `src`, explica por qué y pregúntame antes.
- Las interfaces generadas **no** deben incluir el prefijo `/api/v1` en `@Path`: el prefijo lo pone `quarkus.rest.path`. Revisa el código generado; si el generador agrega el path del `servers.url`, desactívalo en su configuración.
- `Money` es un `String` en los DTOs. La conversión con `BigDecimal` la hace el mapper.
- Las fechas `format: date` se generan como `LocalDate`. Verifica que Jackson las serialice en ISO (`2026-10-15`) y no como arreglo ni timestamp.
- Las interfaces de las operaciones aún no implementadas pueden generarse; sin una clase que las implemente no exponen nada.

## 2. Configuración (`application.yml`)

```yaml
quarkus:
  rest:
    path: /api/v1
  http:
    access-log:
      enabled: false
```

## 3. Implementación de `getCustomerAccountStatus`

| Clase | Ubicación | Responsabilidad |
| --- | --- | --- |
| `CustomerResource` | `ops.web` | Reemplaza a `AccountStatusResource`: implementa la interfaz generada del tag `Customers`; `@IfBuildProfile("dev")` |
| `AccountStatusMapper` | `ops.mapper` | Se adapta (no se crea otro): `account.model.AccountStatus` → `api.model.AccountStatus` |
| `ProblemExceptionMappers` | `ops.web` | `ExceptionMapper`s que producen `application/problem+json` |
| `CustomerNotFoundException` | `account.services` | La lanza el recurso cuando la clienta no está registrada |

Como el DTO generado y el record de dominio se llaman igual (`AccountStatus`), usa nombres calificados o un import con alias en el mapper; no renombres el esquema del contrato.

Comportamiento:

- El path ya viene validado por el patrón `^51\d{9}$`. Si la validación del generador no lo aplica, valida en el recurso y responde 400 con `Problem`.
- Clienta no registrada (`registered == false` en el dominio) → **404** con `Problem` (`title: Customer not found`). Antes devolvía 200 con `registered: false`; el cambio es intencional, porque en REST un recurso inexistente es 404.
- Libro no disponible → **503** con `Problem`.
- Clienta registrada → 200 con el DTO.

Mapeo dominio → DTO (MapStruct, con métodos `@Named` para los casos especiales):

| Dominio (`account.model`, `sheets.model`) | DTO (`api.model`) | Regla |
| --- | --- | --- |
| `BigDecimal` | `Money` (`String`) | `setScale(2, HALF_UP).toPlainString()` |
| `OrderStatus` (valores cobrables) | `GarmentStatus` (`PENDING`, `DELIVERED`) | `@ValueMapping` explícito; cualquier otro valor es un error de programación |
| Catálogo de `CampaignId` | `Catalog` (`PACIFIKA`, `CARMEL`) | Mayúsculas |
| Título de `CampaignId` | `campaignId` | Tal cual el nombre de la pestaña |
| `RecipientGroup` | `RecipientGroup` | El destinatario ("Para") → `recipient` |
| `GarmentInfo` (agotadas / sin precio) | `GarmentReference` → `soldOutItems` / `unpricedItems` | — |
| Fecha de pago (`LocalDate`) | `paymentDueDate` | ISO 8601 |

Los nombres de los campos del dominio pueden diferir; ajusta el mapper a los reales.

`ProblemExceptionMappers`:

- `CustomerNotFoundException` → 404.
- `WorkbookUnavailableException` (o el nombre real) → 503.
- Errores de validación de Bean Validation (`ConstraintViolationException`) → 400 con el detalle del campo.
- `type` = `about:blank`, `title` corto en inglés, `detail` sin datos personales (nunca el celular completo).

## 4. Tests

- Actualizar los tests del endpoint anterior al nuevo path `GET /api/v1/customers/{phone}/account-status` y agregar:
  - `51911111111` (Gaby) → 200; `grandTotal == "175.53"`; la primera campaña es `C-11-carmel` con `paymentDueDate == "2026-10-20"`; el grupo de `C-11-pacifika` con `recipient == "Ana"` tiene `subtotal == "75.83"`.
  - `51999999999` → 404 con `Content-Type: application/problem+json` y `status == 404`.
  - `12345` → 400 con `application/problem+json`.
  - Libro no disponible (con `WorkbookSource` mockeado que falla y sin caché) → 503.
- `AccountStatusApiMapperTest`: montos con 2 decimales como texto, enums, fechas nulas, `recipient` nulo.
- Si `@IfBuildProfile("dev")` impide probar el recurso en el perfil `test`, usa un perfil de test que active la build profile `dev` o prueba el recurso directamente, y documenta la decisión.

## Criterios de aceptación

- [ ] Solo existe `src/main/resources/openapi/openapi.yml`; no quedan otros contratos ni DTOs escritos a mano para la API.
- [ ] `npx @stoplight/spectral-cli lint src/main/resources/openapi/openapi.yml` termina sin errores.
- [ ] `GET /api/v1/customers/{phone}/account-status` responde según la sección 3 y los tests de la sección 4 pasan.
- [ ] Las rutas antiguas responden 404.
- [ ] Los montos se devuelven como texto con 2 decimales y las fechas en ISO 8601.
- [ ] Los errores devuelven `application/problem+json` sin datos personales.
- [ ] `./mvnw verify` pasa y el endpoint no existe en el build de producción (`./mvnw package` + revisión de que el recurso no se registra fuera de `dev`).
- [ ] Prueba manual: `curl -i localhost:8080/api/v1/customers/<celular>/account-status` con el Sheet real.
