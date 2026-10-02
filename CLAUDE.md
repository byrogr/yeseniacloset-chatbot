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

Regla de dependencias: `account` no depende de `whatsapp` ni de `agent`; `sheets` no depende de nadie. El dominio se prueba sin red.

### Subpaquetes dentro de cada paquete de dominio

Dentro de cada paquete de la tabla anterior, el código se separa por responsabilidad técnica en estos subpaquetes (se crean solo los que el dominio necesita; no todos aplican a todos):

| Subpaquete | Contiene |
| --- | --- |
| `model` | Records de valor inmutables del dominio (sin lógica, sin Lombok). |
| `repository` | Puertos de acceso a datos externos (interfaces) y sus adaptadores (p. ej. `WorkbookSource` / `GoogleSheetsWorkbookSource`), más las excepciones propias de esa fuente. |
| `services` | Lógica de negocio: casos de uso, parseo, orquestación y caché. |
| `utils` | Funciones estáticas y sin estado, sin inyección CDI (`@UtilityClass` o clases `final` con métodos estáticos). |
| `config` | `@ConfigMapping` y `@Produces` de infraestructura (p. ej. `Clock`). |
| `web` | Puntos de entrada: recursos JAX-RS y health checks. Solo delegan, sin lógica de negocio. |
| `mapper` | Interfaces MapStruct que convierten entre records de dominio y DTOs generados del contrato OpenAPI. |

Ejemplo actual: `sheets.model.{CampaignId,Customer,OrderRow,OrderStatus,ParsedWorkbook,RawWorkbook}`, `sheets.repository.{WorkbookSource,GoogleSheetsWorkbookSource,WorkbookUnavailableException}`, `sheets.services.{WorkbookParser,WorkbookProvider}`, `sheets.utils.{NameNormalizer,PhoneNormalizer,TabNamePolicy}`, `sheets.config.{SheetsConfig,ClockProducer}`; `account.model.{AccountStatus,CampaignAccount,Garment,GarmentInfo,RecipientGroup}`, `account.services.AccountStatusService`; `ops.web.{AccountStatusResource,SheetsReadinessCheck}`, `ops.mapper.AccountStatusMapper`.

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
- MapStruct para mapear entre modelos (p. ej. DTO generado de un contrato OpenAPI ↔ record de dominio). No escribir mappers a mano si MapStruct puede generarlos.
- Montos: `BigDecimal` con escala 2 y `RoundingMode.HALF_UP`. Nunca `double` para dinero.
- Fechas: `LocalDate`; el Sheet las entrega como texto `dd/MM/yyyy`.
- Inyectar `java.time.Clock` donde haya lógica dependiente del tiempo, para poder probarla.
- Nada de lógica de negocio en recursos REST: solo delegan.
- Javadoc en clases públicas con la etiqueta `@author Roger Rojas - roger.rojas@rmsolutions.pe`.

## APIs REST: API First

- Todo endpoint REST se define primero en un contrato OpenAPI (`src/main/resources/openapi/*.yaml`), y el código se genera o se valida contra ese contrato antes de implementarlo.
- El recurso JAX-RS implementa la interfaz generada a partir del contrato; no se escriben DTOs de request/response a mano si el generador ya los produce.
- MapStruct convierte entre los DTOs generados del contrato y los records de dominio (p. ej. `AccountStatus` → DTO de respuesta).
- Esto aplica a endpoints de producto y también a los de desarrollo (`ops`), salvo que se indique lo contrario para un caso puntual.

## Seguridad y privacidad

- Nunca escribir secretos en el repo: credenciales de la service account, tokens de Meta, claves de Azure. Se leen de variables de entorno o de archivos fuera del repositorio.
- No registrar en logs el texto de mensajes ni celulares completos: enmascarar como `51987***321`.
- La identidad de la clienta sale siempre del celular del webhook, nunca de un parámetro que controle el LLM.

## Comandos

```bash
./mvnw quarkus:dev          # modo desarrollo (lee el Sheet real con las variables de entorno)
./mvnw test                 # tests unitarios y de componente
./mvnw verify               # build completo
./mvnw package -Dnative     # build nativo (requiere GraalVM o contenedor)
```

Variables de entorno en desarrollo:

```bash
export SHEETS_SPREADSHEET_ID=...
export SHEETS_CREDENTIALS_FILE=$HOME/.config/chatbot-pedidos/sa.json
```

## Cómo trabajar en este repo

- Cada fase tiene su spec en `docs/specs/fase-N.md`. Implementa solo lo que está en el alcance de la spec activa.
- Antes de escribir código, presenta un plan y espera confirmación.
- Después de cada paso, corre `./mvnw test`; no avances con tests en rojo.
- Si la spec es ambigua o contradice este archivo, pregunta antes de decidir.

## Estado

- [x] Fase 0: Sheet, Meta (número de prueba), Google Cloud y Azure configurados.
- [x] Fase 1: lectura del Sheet y dominio (`docs/specs/fase-1.md`). Pendiente la verificación manual contra el Sheet real (ver tabla de criterios de aceptación) y la deuda técnica de cobertura de `GoogleSheetsWorkbookSource` (`docs/notas/cobertura-google-sheets.md`).
- [ ] Fase 2: agente de IA.
- [ ] Fase 3: canal WhatsApp.
- [ ] Fase 4: despliegue y piloto.
