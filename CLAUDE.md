# Chatbot de pedidos por WhatsApp (YeseniaClosetBot)

Bot de WhatsApp para un negocio de venta de ropa por catálogo (Pacifika y Carmel). Las clientas preguntan qué prendas pidieron, cuánto es el total y cuándo pagan; el bot responde leyendo un Google Sheet en tiempo real.

## Principio rector

**Los montos nunca los calcula ni los inventa el LLM.** Todo dato de pedidos sale del Google Sheet y se calcula en Java (`BigDecimal`). El LLM solo interpreta la pregunta y redacta la respuesta a partir de lo que devuelven las tools.

## Stack

- Java 21, Quarkus 3.x (última estable), Maven con wrapper (`./mvnw`).
- Configuración en `application.yml` (no `.properties`).
- Lombok y MapStruct en todo el proyecto (ver convenciones).
- Google Sheets API v4 con service account de solo lectura.
- Azure: Microsoft Foundry (modelo mini), Container Apps, Application Insights.
- WhatsApp Cloud API de Meta.
- Tests: JUnit 5, AssertJ, REST Assured.

## Arquitectura (paquete base `pe.rmsolutions.chatbot`)

| Paquete | Responsabilidad | Fase |
| --- | --- | --- |
| `sheets` | Leer el libro, seleccionar pestañas, parsear por encabezado, caché | 1 |
| `account` | Dominio: filtrar por clienta, agrupar por campaña y "Para", totales | 1 |
| `agent` | AI Service de LangChain4j, tools, memoria, contexto de la clienta | 2 |
| `whatsapp` | Webhook, firma HMAC, deduplicación, envío, pausa por eco | 3 |
| `ops` | Health checks y endpoints de desarrollo (solo perfil `dev`) | 1+ |
| `api`, `api.model` | Interfaces y DTOs **generados** desde `openapi.yml` (no se editan a mano) | 1.1 |

Regla de dependencias: `account` no depende de `whatsapp` ni de `agent`; `sheets` no depende de nadie. El dominio se prueba sin red. Solo los subpaquetes `web` y `mapper` dependen de `api` / `api.model`.

### Subpaquetes dentro de cada paquete de dominio

Dentro de cada paquete de la tabla anterior, el código se separa por responsabilidad técnica en estos subpaquetes (se crean solo los que el dominio necesita; no todos aplican a todos):

| Subpaquete | Contiene |
| --- | --- |
| `model` | Records de valor inmutables del dominio (sin lógica, sin Lombok). |
| `repository` | Puertos de acceso a datos o servicios externos (interfaces) y sus adaptadores (p. ej. `WorkbookSource` / `GoogleSheetsWorkbookSource`), más las excepciones propias de esa fuente. |
| `services` | Lógica de negocio: casos de uso, parseo, orquestación y caché. |
| `utils` | Funciones estáticas y sin estado, sin inyección CDI (`@UtilityClass` o clases `final` con métodos estáticos). |
| `config` | `@ConfigMapping` y `@Produces` de infraestructura (p. ej. `Clock`). |
| `web` | Puntos de entrada: recursos JAX-RS que implementan las interfaces generadas, filtros, `ExceptionMapper`s y health checks. Solo delegan, sin lógica de negocio. |
| `mapper` | Interfaces MapStruct que convierten entre records de dominio y DTOs generados del contrato OpenAPI. |

Ejemplo actual: `sheets.model.{CampaignId,Customer,OrderRow,OrderStatus,ParsedWorkbook,RawWorkbook}`, `sheets.repository.{WorkbookSource,GoogleSheetsWorkbookSource,WorkbookUnavailableException}`, `sheets.services.{WorkbookParser,WorkbookProvider}`, `sheets.utils.{NameNormalizer,PhoneNormalizer,TabNamePolicy}`, `sheets.config.{SheetsConfig,ClockProducer}`; `account.model.{AccountStatus,CampaignAccount,Garment,GarmentInfo,RecipientGroup}`, `account.services.AccountStatusService`; `ops.web.{AccountStatusResource,SheetsReadinessCheck}`, `ops.mapper.AccountStatusMapper`. Canal: `whatsapp.web.{WhatsAppWebhookResource,WebhookSignatureFilter}`, `whatsapp.services.{WebhookEventHandler,InboundDispatcher,InboundMessageProcessor,MessageDeduplicator}`, `whatsapp.repository.{OutboundMessenger,CloudApiMessenger,GraphApiClient}`.

Las fases futuras (`agent`, `whatsapp`) siguen esta misma convención desde su primer commit.

## Contrato del Google Sheet (resumen)

- Un archivo por año; su ID va en configuración (`sheets.spreadsheet-id`).
- Pestaña `Clientas`: columnas `Nombre`, `Celular` (`51` + 9 dígitos).
- Pestañas de campaña: nombre `^C-(\d+)-(pacifika|carmel)$`, sin distinguir mayúsculas. Todo lo demás (PLANTILLA, resúmenes) se ignora.
- Columnas de campaña, mapeadas **por nombre de encabezado**, nunca por letra: `Producto, Talla, Código, Página, Clienta, Para, Fecha de pedido, Fecha de pago, Estado, Precio, %, Dscto., Monto total`.
- Estados: `Pendiente` y `Entregado` se cobran; `Agotado` se informa; `Pagado` y `Cancelado` se ignoran.
- Detalle completo: `docs/specs/fase-1.md`.

## Convenciones

- Nomenclatura técnica siempre en inglés: clases, interfaces, métodos, variables y campos (`AccountStatus`, `Garment`, `Gateway`, `Parser`). Se exceptúa el valor textual exacto que viene del Google Sheet (los estados `Pendiente/Entregado/Pagado/Agotado/Cancelado`, el nombre de la pestaña `Clientas`): eso es parte del contrato de datos con el Sheet, no una decisión de naming, y se mantiene en español tal cual lo escribe la tienda.
- Records de Java para objetos de valor inmutables (no llevan Lombok: ya son inmutables por diseño).
- Lombok en el resto de clases (servicios, componentes) para reducir boilerplate: `@RequiredArgsConstructor` para inyección de dependencias, `@Slf4j` para logging, etc. No usar `@Data`/`@Builder` sobre records.
- MapStruct para mapear entre modelos (DTO generado del contrato OpenAPI ↔ record de dominio). No escribir mappers a mano si MapStruct puede generarlos.
- Montos: `BigDecimal` con escala 2 y `RoundingMode.HALF_UP`. Nunca `double` para dinero.
- Fechas: `LocalDate`; el Sheet las entrega como texto `dd/MM/yyyy`.
- Inyectar `java.time.Clock` donde haya lógica dependiente del tiempo, para poder probarla.
- Nada de lógica de negocio en recursos REST: solo delegan.
- Javadoc en clases públicas con la etiqueta `@author Roger Rojas - roger.rojas@rmsolutions.pe`.

## APIs REST: API First

### Un solo contrato

- Todo el backend se describe en **un único archivo**: `src/main/resources/openapi/openapi.yml` (OpenAPI 3.0.3). Nunca crear un contrato por endpoint ni por fase.
- Flujo: primero se diseña o modifica el contrato, luego se genera el código, luego se implementa. Una fase que agrega endpoints empieza editando este archivo.
- El código generado va a `pe.rmsolutions.chatbot.api` (interfaces) y `pe.rmsolutions.chatbot.api.model` (DTOs) y nunca se edita a mano.
- Los recursos del subpaquete `web` implementan las interfaces generadas; los DTOs de request/response son siempre los generados. Los mappers del subpaquete `mapper` (MapStruct) convierten DTO ↔ dominio.
- El contrato se versiona con la API: `info.version` sigue SemVer (minor para cambios compatibles, major para incompatibles).

### Ruta base y versionado

- `servers.url: /api/v1` y `quarkus.rest.path: /api/v1`. Los paths del contrato son relativos (`/customers/...`), sin repetir el prefijo.
- La versión mayor va en la URL. Los cambios compatibles (campos opcionales nuevos, endpoints nuevos) se agregan a `v1`. Un cambio incompatible requiere `v2` y se discute antes de hacerlo.
- Los endpoints del framework (`/q/health`, `/q/openapi`) quedan fuera del contrato.

### Diseño de recursos

- Paths con sustantivos en plural y kebab-case, sin verbos: `/customers/{phone}/account-status`, `/conversations/{phone}/messages`.
- Sub-recursos para relaciones (`/conversations/{phone}/pause`); las acciones se modelan como recursos (`DELETE .../pause` en vez de `POST .../resume`).
- Métodos con su semántica HTTP: `GET` lee, `POST` crea o procesa, `PUT` reemplaza, `PATCH` modifica parcialmente, `DELETE` elimina (idempotente).
- Códigos: 200, 201 (con `Location`), 204, 400, 401, 403, 404, 409, 503. Nada de 200 con un error dentro del cuerpo.
- Errores siempre en `application/problem+json` con el esquema `Problem` (RFC 9457), mediante `ExceptionMapper`s en el subpaquete `web`.

### Uso del OpenAPI Spec

- Cada operación tiene `tags`, `operationId` (camelCase, verbo + recurso, único), `summary` y `description`.
- Reutilizar `components` (`schemas`, `parameters`, `responses`, `examples`); nada de esquemas inline repetidos.
- Esquemas en PascalCase, propiedades en camelCase y enums en UPPER_SNAKE_CASE (en inglés: `PENDING`, no `Pendiente`; MapStruct traduce desde los valores del Sheet).
- Marcar `required` y `nullable` explícitamente. Montos como `Money` (texto `^\d+\.\d{2}$`); fechas con `format: date` o `date-time` (ISO 8601).
- Excepción: los payloads de terceros (webhook de Meta) conservan su formato original (snake_case, `hub.mode`). **No** se declara `additionalProperties: true`: el generador crea DTOs que extienden `HashMap` y no deserializan los campos tipados. Las propiedades extra ya se aceptan por defecto en OpenAPI 3.0 y Jackson las ignora (`quarkus.jackson.fail-on-unknown-properties: false`).
- Operaciones de desarrollo: tag propio y `x-profile: dev`; su implementación lleva `@IfBuildProfile("dev")`, así que en producción no existen.
- Lint obligatorio sin errores: `npx @stoplight/spectral-cli lint src/main/resources/openapi/openapi.yml` (reglas en `.spectral.yaml`).

### Datos personales en la API

- Los celulares solo pueden ir en el path de endpoints `dev`. Nunca en query strings ni en endpoints de producción.
- El access log HTTP queda deshabilitado (lo está por defecto en Quarkus); no activarlo sin enmascarar los paths.

## Seguridad y privacidad

- Nunca escribir secretos en el repo: credenciales de la service account, tokens de Meta, claves de Azure. Se leen de variables de entorno o de archivos fuera del repositorio.
- No registrar en logs el texto de mensajes ni celulares completos: enmascarar como `51987***321`.
- La identidad de la clienta sale siempre del celular del webhook, nunca de un parámetro que controle el LLM.

## Comandos

```bash
./mvnw quarkus:dev          # modo desarrollo (lee el Sheet real con las variables de entorno)
./mvnw test                 # tests unitarios y de componente
./mvnw verify               # build completo
./mvnw verify -Peval        # evaluación del asistente contra el modelo real (Fase 2)
./mvnw package -Dnative     # build nativo (requiere GraalVM o contenedor)
npx @stoplight/spectral-cli lint src/main/resources/openapi/openapi.yml   # lint del contrato
```

Variables de entorno en desarrollo:

```bash
export SHEETS_SPREADSHEET_ID=...
export SHEETS_CREDENTIALS_FILE=$HOME/.config/chatbot-pedidos/sa.json
export WHATSAPP_PHONE_NUMBER_ID=...   # Fase 3
export WHATSAPP_ACCESS_TOKEN=...
export WHATSAPP_APP_SECRET=...
export WHATSAPP_VERIFY_TOKEN=...      # valor aleatorio largo, el mismo que se pone en Meta
```

## Cómo trabajar en este repo

- Cada fase tiene su spec en `docs/specs/fase-N.md`. Implementa solo lo que está en el alcance de la spec activa.
- Antes de escribir código, presenta un plan y espera confirmación.
- Después de cada paso, corre `./mvnw test`; no avances con tests en rojo.
- Si la spec es ambigua o contradice este archivo, pregunta antes de decidir.
- No crear commits (`git commit`) ni hacer `git push`. En su lugar, al terminar el trabajo entrega un plan de commits: para cada commit propuesto, el mensaje y la lista exacta de archivos a agregar (`git add`). El dueño del repo revisa y ejecuta los commits y el push.

## Estado

- [x] Fase 0: Sheet, Meta (número de prueba), Google Cloud y Azure configurados.
- [x] Fase 1: lectura del Sheet y dominio (`docs/specs/fase-1.md`). Pendiente la verificación manual contra el Sheet real (ver tabla de criterios de aceptación) y la deuda técnica de cobertura de `GoogleSheetsWorkbookSource` (`docs/notas/cobertura-google-sheets.md`).
- [x] Fase 1.1: contrato OpenAPI único (`docs/specs/fase-1-1.md`). Pendiente la prueba manual con `curl` contra el Sheet real. Los recursos `dev` usan `@IfBuildProfile(anyOf = {"dev", "test"})` para poder probarlos por HTTP.
- [x] Fase 2: agente de IA (`docs/specs/fase-2.md`). Eval contra el modelo real: 48/48, guardia 0, p95 2,7 s (`./mvnw verify -Peval` con las variables `FOUNDRY_*`). Pendiente la prueba manual con una clienta real. `FOUNDRY_ENDPOINT` debe ser la URL completa del deployment (`https://<recurso>.openai.azure.com/openai/deployments/<deployment>`). `FOUNDRY_API_VERSION` es la `api-version` que el portal muestra en el ejemplo del deployment.
- [x] Fase 3: canal WhatsApp (`docs/specs/fase-3.md`). Contrato en 1.1.0 (se quitó `additionalProperties: true` de los esquemas de Meta). Prueba con el número de prueba (sección 7 de la spec): webhook verificado por túnel y respuesta real entregada en WhatsApp a partir de un mensaje simulado y firmado desde Postman. Pendiente: mensaje real desde el celular (la app sin publicar solo recibe webhooks de prueba del panel; puede requerir publicarla en la Fase 4) y los chequeos de segundo mensaje, fuera de alcance y audio contra Meta. El formato de `smb_message_echoes` se tomó de la documentación de coexistencia y no se pudo probar con el número de prueba; los ecos `revoke`/`edit` también pausan.
  - Pendiente de IA Responsable: responder a los mensajes que no son texto (audio, imagen) con un aviso en vez de silencio; hoy la spec dice que no se responde.
- [ ] Fase 4: despliegue y piloto.
  - Pendientes de IA Responsable: nota de transparencia en `docs/`; métricas en Application Insights (tasa de guardia y de derivaciones); volver a correr la eval ante cada cambio de modelo o prompt; definir con la dueña cómo se informa a las clientas del uso de IA y de Azure.
