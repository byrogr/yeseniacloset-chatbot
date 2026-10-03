# Fase 4 — Despliegue y piloto

## Objetivo

Poner el bot en producción en Azure Container Apps, conectado a un **número nuevo dedicado al bot** por la Cloud API de Meta, y validarlo con un piloto de 3 a 5 clientas antes de abrirlo a todas.

## Cómo leer esta spec

Cada parte indica quién la hace:

- **[Claude Code]**: código, configuración y workflows del repo. Es lo que ejecuta `/implementar-fase 4`.
- **[Manual]**: pasos en portales (Azure, GitHub, Meta) que hace el dueño del repo. Claude Code no los ejecuta, pero al cerrar la fase debe listarlos como pendientes en la tabla de criterios.

## Decisión previa: número nuevo, sin coexistencia

Se descartó la coexistencia con el número de la dueña (Tech Provider propio o BSP). El bot usa un **número nuevo** registrado en la cuenta de WhatsApp Business de la app de Meta propia, con la Cloud API directa, igual que el número de prueba de la Fase 3.

Consecuencia principal: **nadie atiende el número del bot a mano**. La dueña no tiene app en ese número, así que no ve los mensajes ni puede responder desde él, y no hay ecos que pausen al bot. Toda derivación debe mandar a la clienta al número personal de la dueña (parte A6), y ningún mensaje del bot puede prometer que ella "le escribirá".

La pausa por eco y el registro de pausas de la Fase 3 se conservan sin cambios: no estorban y permiten migrar más adelante a coexistencia (ver Decisiones).

## Alcance

**Incluye**: interruptor de apagado y lista de piloto, aviso para mensajes que no son texto, métricas en Application Insights, imagen de contenedor, CI/CD con GitHub Actions, despliegue en Container Apps, publicación de la app de Meta, registro del número nuevo, derivación al número de la dueña, nota de transparencia, comunicación a clientas y piloto.

**No incluye**: coexistencia, bandeja de atención web para la dueña, notificaciones a la dueña, persistencia externa de pausas o memoria, dominio propio, build nativo y multiinstancia. Ver "Decisiones" al final.

**Contrato OpenAPI**: esta fase no agrega endpoints. Si alguna tarea parece requerirlo, detente y pregunta.

---

## Parte A — Código [Claude Code]

### A1. Interruptor de apagado y lista de piloto

Agregar a `agent.config.BotConfig`:

```yaml
bot:
  enabled: ${BOT_ENABLED:true}
  allowlist: ${BOT_ALLOWLIST:}       # celulares separados por coma; vacío = todas las clientas
```

En `whatsapp.services.InboundMessageProcessor`, antes de cualquier otra lógica:

1. Si `bot.enabled == false`, no llama al agente y responde el aviso de redirección de A6.
2. Si `allowlist` no está vacía y el celular normalizado no está en ella, no llama al agente y responde el aviso de redirección de A6.
3. Los ecos (`smb_message_echoes`) siguen pausando siempre, aunque el bot esté apagado o el celular fuera de la lista.

Como nadie lee el número del bot, ignorar en silencio dejaría a la clienta sin respuesta. El aviso de redirección es un texto fijo, sin LLM, así que es seguro aunque el bot esté apagado por un problema del modelo. Se envía **como máximo una vez por celular cada 12 h** (mismo mecanismo que A2).

Registra una métrica (A3) para cada mensaje que no pasa por el agente, con el motivo (`disabled` o `not_allowlisted`).

### A2. Aviso para mensajes que no son texto (pendiente de IA responsable)

Reemplaza el silencio de la Fase 3 por un aviso:

- Si el mensaje no es texto (audio, imagen, sticker, video, documento, ubicación), el celular está permitido (A1) y la conversación **no** está pausada, envía:

  `Hola, soy el asistente automático de {ownerName}. Solo puedo leer mensajes de texto. Escríbeme tu consulta o, si prefieres enviar audios o fotos, escríbele a {ownerName}: {ownerContactLink}`

  (`{ownerContactLink}` se define en A6).

- Como máximo **un aviso por celular cada 12 h**, para no responder a cada audio de una ráfaga. Usa `whatsapp.services.NoticeThrottle` (Caffeine con `expireAfterWrite`, `Ticker` desde el `Clock`), con una clave por tipo de aviso (`non_text`, `redirect`) para que A1 y A2 compartan el mecanismo sin pisarse.
- El aviso no pausa la conversación.
- Actualiza los tests de la Fase 3 que esperaban silencio.

### A3. Observabilidad

Dependencias:

- `io.quarkus:quarkus-opentelemetry` (si no está ya).
- `io.quarkiverse.opentelemetry.exporter:quarkus-opentelemetry-exporter-azure`, en la versión compatible con la de Quarkus.

Configuración:

```yaml
quarkus:
  otel:
    metrics:
      enabled: true
    azure:
      applicationinsights:
        connection-string: ${APPLICATIONINSIGHTS_CONNECTION_STRING:}

"%dev":
  quarkus:
    otel:
      sdk:
        disabled: true

"%test":
  quarkus:
    otel:
      sdk:
        disabled: true
```

Verifica los nombres exactos de las propiedades del exportador en su documentación.

Métricas de negocio en una clase nueva, `observability.services.BotMetrics`. Crea el paquete de nivel superior `observability`: no depende de ningún otro paquete y lo usan `agent` y `whatsapp`. Agrégalo a la tabla de arquitectura de `CLAUDE.md`.

| Métrica | Tipo | Atributos | Dónde se registra |
| --- | --- | --- | --- |
| `chatbot.turns` | Counter | `outcome`: `replied`, `paused`, `error` | `ChatService` |
| `chatbot.turn.duration` | Histogram (ms) | — | `ChatService` |
| `chatbot.guard.triggered` | Counter | — | `ChatService` |
| `chatbot.handoffs` | Counter | — | `OrderTools.handOffToOwner` |
| `chatbot.messages.ignored` | Counter | `reason`: `disabled`, `not_allowlisted`, `notice_suppressed` | `InboundMessageProcessor` |
| `chatbot.messages.non_text` | Counter | — | `InboundMessageProcessor` |
| `chatbot.outbound.errors` | Counter | `metaErrorCode` | `CloudApiMessenger` |
| `chatbot.webhook.signature_invalid` | Counter | — | `WebhookSignatureFilter` |

**Ninguna métrica ni atributo lleva celulares, nombres ni texto.** Los motivos de derivación tampoco van como atributo, porque los escribe el LLM y podrían contener datos de la clienta.

Tests: `BotMetricsTest` con el SDK de OpenTelemetry en memoria (`InMemoryMetricReader`), verificando que cada punto incrementa la métrica correcta.

### A4. Configuración de producción

En `application.yml`, perfil `%prod`:

- `quarkus.langchain4j.azure-openai.log-requests` y `log-responses`: `false` (ya debería estar; verificar).
- `quarkus.log.level: INFO`, y `WARN` para los paquetes de Google y LangChain4j.
- `quarkus.http.access-log.enabled: false`.
- Ningún valor por defecto con datos reales: todo sale de variables de entorno.

Agrega `docs/operacion/variables-entorno.md` con la tabla completa de variables (nombre, si es secreto, de dónde sale el valor y fase en que se agregó). Es la referencia para configurar Container Apps.

### A5. Imagen de contenedor

- Extensión `quarkus-container-image-jib`. Construye y publica la imagen sin Docker local.
- Configuración (en el perfil por defecto, sobrescribible por variables en CI):

```yaml
quarkus:
  container-image:
    registry: ghcr.io
    group: ${CONTAINER_IMAGE_GROUP:}   # usuario de GitHub, en minúsculas
    name: yeseniacloset-chatbot
    build: false
    push: false
  jib:
    base-jvm-image: registry.access.redhat.com/ubi9/openjdk-21-runtime
    platforms: linux/amd64
```

- Imagen **JVM**, no nativa: con una réplica siempre activa (ver Decisiones), el tiempo de arranque deja de importar y se evita el riesgo de la librería de Google en nativo documentado en la Fase 1.
- Prueba local: `./mvnw package -Dquarkus.container-image.build=true` debe terminar sin errores.

### A6. Derivación al número de la dueña

Hoy toda derivación responde "{ownerName} te escribirá pronto" y pausa la conversación 12 h, esperando que la dueña responda desde su app. Con un número dedicado eso deja a la clienta sin respuesta. Se reemplaza por una redirección.

Configuración nueva en `agent.config.BotConfig`:

```yaml
bot:
  owner-contact-phone: ${BOT_OWNER_CONTACT_PHONE:}   # 51 + 9 dígitos; vacío = modo coexistencia (comportamiento de la Fase 3)
```

- Validación al arrancar: si no está vacío, debe cumplir `^51\d{9}$` (usar `PhoneNormalizer`).
- `{ownerContactLink}` = `https://wa.me/<owner-contact-phone>`. WhatsApp lo muestra como enlace que abre el chat con la dueña.
- El número de la dueña no es secreto, pero es un celular: **no se registra en logs** ni va en métricas.

Textos, centralizados en una clase nueva `agent.services.HandoffMessages` (la usan `ChatService`, `AccountStatusFormatter` y `InboundMessageProcessor`, para no repetir cadenas):

| Situación | Hoy | Con `owner-contact-phone` |
| --- | --- | --- |
| Derivación (`handOffToOwner`, filtro de contenido) | `{ownerName} te escribirá pronto.` | `Eso lo ve directamente {ownerName}. Escríbele aquí: {ownerContactLink}` |
| Sheet o modelo no disponibles | `En este momento no puedo revisar tu pedido. {ownerName} te escribirá pronto.` | `En este momento no puedo revisar tu pedido. Inténtalo más tarde o escríbele a {ownerName}: {ownerContactLink}` |
| Clienta no registrada | `Hola, {ownerName} te escribirá pronto para ayudarte con tu pedido.` | `No encuentro pedidos con este número. Escríbele a {ownerName}: {ownerContactLink}` |
| Aviso de redirección (A1) | — | `Hola, soy el asistente automático de {ownerName}. En este momento no estoy atendiendo; escríbele a {ownerName}: {ownerContactLink}` |
| Cierre con total | `Si ya hiciste algún pago o adelanto, {ownerName} lo descuenta.` | Sin cambio |

Comportamiento con `owner-contact-phone` configurado:

- `handOffToOwner` y los respaldos **no pausan** la conversación: si la clienta vuelve a preguntar por su pedido, el bot le responde. La métrica `chatbot.handoffs` se registra igual.
- Con `owner-contact-phone` vacío, todo queda como en la Fase 3 (pausa y "te escribirá pronto"). Así, migrar a coexistencia es un cambio de configuración.

Texto de derivación determinista, no redactado por el LLM:

- Si `handOffToOwner` se ejecutó en el turno, `ChatService` **reemplaza** la respuesta del modelo por `HandoffMessages.handoff()`, en ambos modos. Mismo principio que los montos: el enlace y el número nunca pasan por el LLM, así que no puede copiarlos mal ni inventarlos.
- `prompts/system-prompt.txt`: en el tipo B y la regla de `registered=false` (hoy la 7), cambiar "responde que {ownerName} le escribirá pronto" por "responde en una sola línea breve". No se agrega ninguna variable nueva al prompt.
- Volver a correr la eval (`./mvnw verify -Peval`): debe seguir en 48/48 con la guardia en 0. La eval mide la tool elegida, así que el reemplazo del texto no la afecta, pero el prompt cambió.

Tests:

- `HandoffMessagesTest`: ambos modos, enlace bien formado.
- `ChatServiceTest` y `AccountStatusFormatterTest`: los casos que esperan "te escribirá pronto" se duplican para el modo con número; en ese modo la conversación no queda pausada. Un turno con `handOffToOwner` devuelve el texto de `HandoffMessages` aunque el modelo haya escrito otra cosa.
- `InboundMessageProcessor`: bot apagado y celular fuera de la lista responden la redirección una vez en 12 h.
- Arranque con `owner-contact-phone` inválido falla con un mensaje claro.

---

## Parte B — CI/CD [Claude Code escribe; Manual configura]

Tres workflows en `.github/workflows/`.

### B1. `ci.yml`: en cada pull request y push a `main`

1. `actions/setup-java` (Temurin 21) con caché de Maven.
2. `./mvnw -B verify`.
3. Lint del contrato: `npx @stoplight/spectral-cli lint src/main/resources/openapi/openapi.yml`.

### B2. `deploy.yml`: push a `main` (solo si `ci` pasa) y ejecución manual

```yaml
permissions:
  contents: read
  packages: write
  id-token: write
environment: production
```

1. Construye y publica la imagen con Jib en `ghcr.io/<usuario>/yeseniacloset-chatbot:<sha>` y `:latest`, autenticando con `GITHUB_TOKEN` (`QUARKUS_CONTAINER_IMAGE_USERNAME=${{ github.actor }}`, `QUARKUS_CONTAINER_IMAGE_PASSWORD=${{ secrets.GITHUB_TOKEN }}`).
2. `azure/login@v2` con OIDC (`client-id`, `tenant-id` y `subscription-id` desde variables del environment, sin secretos).
3. `az containerapp update -n ca-chatbot-pedidos -g rg-chatbot-pedidos --image ghcr.io/<usuario>/yeseniacloset-chatbot:<sha>`.
4. Espera a que la revisión nueva esté `Healthy` y llama a `/q/health/ready` de la URL pública; si falla, el job falla.

El environment `production` de GitHub debe tener al dueño del repo como revisor obligatorio, de modo que cada despliegue requiera su aprobación.

### B3. `eval.yml`: evaluación del asistente

- Disparadores: ejecución manual (`workflow_dispatch`) y pull requests que toquen `src/main/resources/prompts/**` o la configuración del modelo en `application.yml`.
- Corre `./mvnw -B verify -Peval` con los secretos `FOUNDRY_ENDPOINT`, `FOUNDRY_API_KEY`, `FOUNDRY_DEPLOYMENT` y `FOUNDRY_API_VERSION`.
- Publica la tabla resumen de la eval en el resumen del job (`$GITHUB_STEP_SUMMARY`).
- **Regla de IA responsable**: un cambio de prompt no se mergea sin una eval en verde. Un cambio de modelo o de deployment en Foundry, que no pasa por el repo, se valida ejecutando este workflow a mano antes de apuntar producción al deployment nuevo.

---

## Parte C — Azure [Manual, portal]

### C1. Acceso a GHCR

1. En GitHub: **Settings → Developer settings → Personal access tokens → Fine-grained** o classic, con solo `read:packages`. Será la credencial de Container Apps para descargar la imagen.
2. En la Container App `ca-chatbot-pedidos`: **Configuración → Registros → Agregar**: servidor `ghcr.io`, usuario de GitHub y el token como contraseña.

### C2. Identidad para GitHub Actions (OIDC)

1. **Microsoft Entra ID → Registros de aplicaciones → Nuevo registro**: `gh-chatbot-pedidos-deploy`.
2. En el registro: **Certificados y secretos → Credenciales federadas → Agregar**, escenario *GitHub Actions*: tu usuario, el repositorio, entidad **Environment** = `production`.
3. En la Container App: **Control de acceso (IAM) → Agregar asignación de roles**: rol **Colaborador**, asignado a `gh-chatbot-pedidos-deploy`, con alcance solo en la Container App.
4. En GitHub, environment `production` → variables: `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`. Son identificadores, no secretos.

### C3. Configuración de la Container App

1. **Secretos**: crea uno por cada valor secreto de `docs/operacion/variables-entorno.md` (tokens de WhatsApp, App Secret, verify token, JSON de la service account de Google, API key de Foundry, connection string de Application Insights).
2. **Contenedor → Editar e implementar → Variables de entorno**: las no secretas como valor y las secretas como "Referencia a secreto". Durante el piloto, `BOT_ALLOWLIST` lleva los celulares de las clientas del piloto.
3. **Recursos**: 0,5 vCPU y 1 GiB.
4. **Sondeos**: liveness `GET /q/health/live` y readiness `GET /q/health/ready`, puerto 8080.
5. **Escalado**: mínimo **1** y máximo **1** réplica (ver Decisiones).
6. **Entrada**: tráfico desde cualquier lugar, puerto de destino **8080** (la imagen de inicio rápido usaba 80).

### C4. Foundry con Managed Identity (recomendado, no bloqueante)

1. En la Container App: **Identidad → Asignada por el sistema → Activado**.
2. En el recurso de Foundry: **Control de acceso (IAM)**, rol **Cognitive Services OpenAI User** para la identidad de la Container App.
3. Si la extensión de LangChain4j del proyecto soporta autenticación con Entra ID, Claude Code configura el uso de la identidad y se borra el secreto de la API key. Si no la soporta, se mantiene la API key como secreto y se documenta en `docs/notas/foundry-managed-identity.md`.

### C5. Alertas (Application Insights → Alertas → Crear regla, notificación por correo)

| Alerta | Condición | Por qué |
| --- | --- | --- |
| Guardia de montos | `chatbot.guard.triggered` > 0 en 1 h | El modelo intentó dar un monto incorrecto; revisar el prompt |
| Errores de envío | `chatbot.outbound.errors` > 3 en 15 min | Token vencido, número desconectado o problema de Meta |
| Firmas inválidas | `chatbot.webhook.signature_invalid` > 10 en 15 min | Posible abuso del webhook |
| Disponibilidad | Readiness DOWN o reinicios de la réplica | El bot no responde |

---

## Parte D — Meta [Manual]

### D1. Publicar la app

Mientras la app no esté publicada, solo recibe los webhooks de prueba del panel (lo que se observó en la Fase 3).

1. **Configuración de la aplicación → Básica**: ícono, categoría y **URL de la política de privacidad**. Puede ser la nota de transparencia de la parte E, publicada como página (por ejemplo, con GitHub Pages).
2. **Publicar** → cambiar la app a modo en vivo.
3. Callback del webhook: `https://<url-de-la-container-app>/api/v1/webhooks/whatsapp`, con el `WHATSAPP_VERIFY_TOKEN` de producción. Suscribir `messages` (sin coexistencia no llegan `smb_message_echoes`).
4. Con el número de prueba y el bot ya en Azure, repetir los chequeos pendientes de la Fase 3: mensaje real desde el celular, segundo mensaje sin presentación, pregunta fuera de alcance que pausa y audio que recibe el aviso de A2.

### D2. Registro del número nuevo

**Antes de empezar:** conseguir un chip (prepago sirve) que **no** tenga WhatsApp activo y que reciba SMS o llamadas. Si tuvo WhatsApp, borrar primero esa cuenta desde la app. Dejar `BOT_ENABLED=false` hasta terminar este paso.

1. **WhatsApp Manager → Números de teléfono → Agregar número**, en la misma cuenta de WhatsApp Business donde está el número de prueba.
2. Nombre visible (Meta lo revisa; debe coincidir con el negocio, por ejemplo "Yesenia Closet") y categoría.
3. Verificación por SMS o llamada y PIN de verificación en dos pasos. Guardar el PIN fuera del repo.
4. Si WhatsApp Manager lo pide, agregar un método de pago. Las respuestas a mensajes que inicia la clienta, dentro de las 24 h, no se cobran, pero revisar la tabla de precios vigente de Meta para Perú.
5. **Token permanente**: en Meta Business Suite → Usuarios del sistema, crear un usuario de sistema con acceso a la app y a la cuenta de WhatsApp, y generar un token con `whatsapp_business_messaging` y `whatsapp_business_management`. El token temporal del panel vence en 24 h.
6. Actualizar los secretos de la Container App: `WHATSAPP_PHONE_NUMBER_ID` del número nuevo y el token permanente. Configurar `BOT_OWNER_CONTACT_PHONE` con el número personal de la dueña.
7. Verificación de negocio en Meta: opcional para el piloto (el bot solo responde mensajes que inician las clientas). Hacerla si se necesita el check verde o más límite para mensajes iniciados por el negocio.

Para verificar, con `BOT_ENABLED=true` y `BOT_ALLOWLIST` con **solo tu celular**:

- El bot te responde una consulta de pedido.
- Una pregunta fuera de alcance recibe el enlace `wa.me` de la dueña, el enlace abre su chat, y la siguiente consulta de pedido se responde (no quedó pausado).
- Desde otro celular fuera de la lista recibes la redirección una sola vez.
- Un audio recibe el aviso de A2.

---

## Parte E — IA responsable [Claude Code redacta; Manual valida con la dueña]

### E1. Nota de transparencia (`docs/transparencia.md`)

Redactada en español sencillo, para la dueña y sus clientas. Debe cubrir:

- **Qué hace el asistente**: responde qué prendas pidió la clienta, el total y la fecha de pago. Nada más.
- **Qué no hace**: no toma pedidos, no confirma pagos, no da descuentos, no responde sobre stock ni catálogos. Todo eso lo atiende la dueña.
- **Cómo decide los montos**: salen siempre de la hoja de pedidos y se calculan sin IA. La IA solo redacta la respuesta, y una verificación automática bloquea cualquier monto que no coincida.
- **Qué datos usa**: el número de celular para identificar a la clienta, el texto del mensaje y los pedidos registrados en la hoja.
- **Dónde se procesan**: Meta (WhatsApp), Microsoft Azure (servidor y modelo de IA en Microsoft Foundry) y Google (hoja de pedidos). Describe, con enlace a la documentación vigente de Microsoft, cómo trata Azure los datos enviados al modelo (si se usan o no para entrenamiento y qué retiene por monitoreo de abuso). No afirmes nada que la documentación no diga.
- **Qué se guarda**: el bot no guarda el texto de los mensajes. Solo mantiene en memoria la conversación de los últimos 30 minutos para dar contexto, y la pierde al reiniciarse. Los registros técnicos no incluyen mensajes ni celulares completos.
- **Límites conocidos**: puede no entender mensajes ambiguos o con errores; no lee audios ni imágenes; si la hoja no está actualizada, la respuesta tampoco lo estará.
- **Supervisión humana**: el número del bot es automático y nadie lo lee. Para hablar con la dueña, la clienta le escribe a su número personal; el bot comparte el enlace cada vez que un tema está fuera de alcance. Cómo se apaga el bot.
- **Contacto** para dudas o para pedir que no se use el asistente con su número.

### E2. Comunicación a las clientas (`docs/comunicacion-clientas.md`)

Dos borradores para que la dueña elija y ajuste:

1. **Mensaje para las clientas del piloto** (individual, enviado por la dueña desde su número, antes de agregarlas a la lista): que hay un número nuevo **solo para consultar pedidos**, con el enlace `wa.me` del bot; que es automático y usa IA; qué puede responder; que para todo lo demás le siguen escribiendo a ella a su número de siempre; y la opción de no participar.
2. **Anuncio general** para abrirlo a todas (por estado de WhatsApp o mensaje): breve, con el número del bot, el aviso de que su número personal sigue igual y el enlace a la nota de transparencia.

Para la dueña: el tratamiento de datos personales en Perú se rige por la Ley 29733. Si hay dudas sobre la necesidad de consentimiento o registro, se consulta con un especialista. La nota no es asesoría legal.

---

## Parte F — Piloto [Manual]

1. La dueña elige 3 a 5 clientas de confianza (al menos una revendedora con "Para") y les envía el mensaje de E2. Las demás clientas no conocen el número todavía; la lista de piloto es una segunda barrera.
2. Se cargan sus celulares en `BOT_ALLOWLIST`.
3. Duración: **2 semanas**.
4. **Revisión semanal** (15 minutos con la dueña), con Application Insights abierto:
    - Turnos respondidos, derivaciones y avisos por mensajes que no son texto.
    - Guardia de montos: debe ser 0. Cualquier activación se revisa.
    - Errores de envío.
    - Lo que la dueña notó: respuestas raras, quejas, consultas que igual le llegaron, y si las derivaciones le llegaron a su número con contexto suficiente.
    - Correcciones al Sheet que hicieron falta (nombres mal escritos, fechas vacías).
5. **Criterios para abrir a todas**: ninguna respuesta con montos incorrectos; la dueña confirma que bajaron las consultas de "cuánto debo"; ninguna clienta pidió dejar de usarlo, o si alguna lo pidió, se resolvió.
6. **Apertura**: anuncio general de E2 y luego `BOT_ALLOWLIST` vacío.

**Plan de reversa** (de más rápido a más drástico):

1. `BOT_ENABLED=false` en la Container App: genera una revisión nueva en cerca de un minuto. El bot deja de usar el modelo y solo responde la redirección al número de la dueña.
2. Quitar la suscripción del webhook en la app de Meta: el número deja de responder por completo.
3. Dar de baja el número en WhatsApp Manager. La dueña no pierde nada: su número y su app nunca se tocaron.

---

## Decisiones de esta fase

| Decisión | Motivo | Cuándo revisarla |
| --- | --- | --- |
| Réplicas: mínimo 1, máximo 1 | Las pausas, la memoria y la deduplicación están en memoria. Con 0 réplicas se perderían al apagarse por inactividad, y el bot volvería a responder en chats que la dueña tomó a mano. Con más de una réplica, cada una tendría su propio estado. | Si hace falta más de una réplica, o si las pausas perdidas en cada despliegue causan problemas: mover las pausas a un almacén externo (por ejemplo, Azure Table Storage) |
| Cada despliegue pierde las pausas | Es el mismo motivo: estado en memoria. | Desplegar fuera del horario de atención |
| Imagen JVM | Con una réplica fija, el arranque no importa; se evita el riesgo de nativo con la librería de Google. | Si se vuelve a escalar a cero |
| URL por defecto de Container Apps | Meta solo necesita HTTPS público. | Si se quiere un dominio propio |
| Número nuevo en vez de coexistencia | Evita la verificación de negocio y el App Review como Tech Provider (ruta A) y la licencia de un BSP (ruta B). La app de la dueña no cambia. | Si las clientas no adoptan el número nuevo, o si la dueña quiere ver y responder los chats del bot. Migrar a coexistencia es vaciar `BOT_OWNER_CONTACT_PHONE` y conectar el número de la dueña |
| Derivación por enlace `wa.me`, sin notificar a la dueña | Notificarla por WhatsApp requiere plantillas y mensajes iniciados por el negocio (con costo y aprobación). La clienta escribe ella misma, con su propio contexto. | Si la dueña pierde consultas o pide enterarse de las derivaciones |

## Criterios de aceptación

**Código y CI [Claude Code]**

- [ ] `./mvnw verify` y el lint de Spectral pasan; no cambió el contrato.
- [ ] Tests de A1: bot apagado y celular fuera de la lista no llegan al agente y reciben la redirección una vez cada 12 h; un eco pausa en ambos casos.
- [ ] Tests de A2: un audio recibe el aviso con el enlace; un segundo audio dentro de las 12 h no lo recibe; un chat pausado no lo recibe.
- [ ] `BotMetricsTest` pasa y ninguna métrica lleva datos personales (revisión de código).
- [ ] `./mvnw package -Dquarkus.container-image.build=true` construye la imagen.
- [ ] Los 3 workflows existen, y `ci.yml` pasa en un pull request.
- [ ] `docs/operacion/variables-entorno.md`, `docs/transparencia.md` y `docs/comunicacion-clientas.md` existen.
- [ ] `CLAUDE.md` actualizado: paquete `observability` en la arquitectura y estado de la fase.
- [ ] A6: con `owner-contact-phone`, ninguna respuesta dice "te escribirá", las derivaciones llevan el enlace y no pausan; sin él, el comportamiento de la Fase 3 no cambia (tests de ambos modos).
- [ ] Eval tras el cambio de prompt: 48/48 o más, guardia 0.

**Despliegue [Manual]**

- [ ] `deploy.yml` desplegó en Container Apps con aprobación del environment, y `/q/health/ready` responde UP en la URL pública.
- [ ] La app de Meta está publicada; un mensaje real desde el celular al número de prueba recibe respuesta.
- [ ] Las métricas aparecen en Application Insights y las 4 alertas están creadas.
- [ ] El número nuevo está registrado con nombre visible aprobado y token permanente; pasan los chequeos de D2.
- [ ] La dueña validó la nota de transparencia y el mensaje del piloto.

**Piloto [Manual]**

- [ ] 2 semanas de piloto sin montos incorrectos y con la guardia en 0 (o cada activación revisada y corregida).
- [ ] Decisión documentada en `CLAUDE.md`: abrir a todas, extender el piloto o revertir.
