# Fase 3 — Canal WhatsApp

## Objetivo

Conversación real por WhatsApp con el número de prueba de Meta: el webhook recibe el mensaje, `ChatService` (Fase 2) genera la respuesta y la Cloud API la envía.

## Antes de empezar

- Clases de la Fase 2 que se reutilizan: `agent.services.ChatService`, `agent.model.ChatReply`, `agent.services.ConversationPauseRegistry`, `agent.config.BotConfig`. Verifica sus nombres reales en el repo.
- Las operaciones del webhook ya están en `src/main/resources/openapi/openapi.yml` (tag `Webhooks`). No crear otro contrato.

## Alcance

**Incluye**: implementación de las operaciones del tag `Webhooks`, validación de firma, deduplicación, procesamiento asíncrono ordenado por celular, filtro de tipos de mensaje, pausa por eco, cliente REST para enviar mensajes, pruebas con túnel y el número de prueba.

**No incluye**: conectar el número de la dueña (Fase 4), plantillas, envío de multimedia, marcar como leído, Dockerfile y despliegue.

## 1. Dependencias

- `quarkus-rest-client-jackson`
- `quarkus-smallrye-fault-tolerance`
- Test: `org.awaitility:awaitility`

## 2. Configuración (`application.yml`)

```yaml
whatsapp:
  api-base-url: https://graph.facebook.com
  api-version: v25.0
  phone-number-id: ${WHATSAPP_PHONE_NUMBER_ID}
  access-token: ${WHATSAPP_ACCESS_TOKEN}
  app-secret: ${WHATSAPP_APP_SECRET}
  verify-token: ${WHATSAPP_VERIFY_TOKEN}
  dedupe-ttl: 24h
  max-text-length: 4096

quarkus:
  rest-client:
    whatsapp-graph:
      url: ${whatsapp.api-base-url}
      connect-timeout: 5000
      read-timeout: 10000
```

`WHATSAPP_VERIFY_TOKEN` lo inventas tú: un valor aleatorio largo. Mapear con `@ConfigMapping(prefix = "whatsapp")` en `whatsapp.config.WhatsAppConfig`.

## 3. Operaciones del tag `Webhooks` (API First)

| operationId | Método y ruta |
| --- | --- |
| `verifyWhatsAppWebhook` | `GET /api/v1/webhooks/whatsapp` |
| `receiveWhatsAppEvents` | `POST /api/v1/webhooks/whatsapp` |

Los esquemas (`WhatsAppWebhookEvent` y relacionados) ya están en el contrato, con `additionalProperties: true` para que los campos nuevos de Meta no rompan el parseo. Si al implementar descubres que falta un campo que el código necesita, agrégalo al contrato, pasa el lint y sube la versión minor de `info.version`.

**`verifyWhatsAppWebhook`**: si `hub.mode == "subscribe"` y `hub.verify_token` coincide con `whatsapp.verify-token`, responde 200 `text/plain` con `hub.challenge` tal cual. En cualquier otro caso, 403 con `Problem`.

**`receiveWhatsAppEvents`**: siempre responde 200 rápido, salvo firma inválida (401 con `Problem`). Ver secciones 4 y 5.

Ejemplo de mensaje de texto, para el fixture `src/test/resources/fixtures/webhook/text-message.json`:

```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "WABA_ID",
    "changes": [{
      "field": "messages",
      "value": {
        "messaging_product": "whatsapp",
        "metadata": { "display_phone_number": "15550000000", "phone_number_id": "TEST_PHONE_NUMBER_ID" },
        "contacts": [{ "wa_id": "51911111111", "profile": { "name": "Gaby" } }],
        "messages": [{
          "from": "51911111111",
          "id": "wamid.TEST001",
          "timestamp": "1790900000",
          "type": "text",
          "text": { "body": "hola cuanto era lo mio?" }
        }]
      }
    }]
  }]
}
```

Crea también estos fixtures, con la misma estructura:

| Archivo | Contenido |
| --- | --- |
| `text-message.json` | El ejemplo de arriba |
| `image-message.json` | `type: "image"`, sin `text` |
| `status-update.json` | Solo `statuses` (confirmación de entrega), sin `messages` |
| `echo-message.json` | `field: "smb_message_echoes"`, `message_echoes` con `from` = número del negocio y `to` = `51911111111` |
| `two-messages.json` | Dos mensajes de texto del mismo celular en un solo payload |
| `other-phone-number-id.json` | Mensaje con un `phone_number_id` distinto al configurado |

El formato de `smb_message_echoes` solo existe con coexistencia y no se puede probar con el número de prueba. Toma la estructura de la documentación actual de Meta, y si difiere de este esquema, ajusta el contrato y avísame.

## 4. Firma del webhook

`WebhookSignatureFilter` (`@ServerRequestFilter`), solo para `POST /api/v1/webhooks/whatsapp`:

1. Lee el cuerpo **crudo** en bytes antes de la deserialización.
2. Calcula `HMAC-SHA256(app-secret, cuerpo)` en hexadecimal minúsculo.
3. Compara con el header `X-Hub-Signature-256` (formato `sha256=<hex>`) usando `MessageDigest.isEqual`, en tiempo constante.
4. Si falta el header o no coincide: aborta con 401 (`application/problem+json`) y registra un warning sin el cuerpo.
5. Si coincide: restaura el stream del cuerpo para que el recurso lo deserialice normalmente.

La firma se calcula sobre los bytes exactos recibidos. Nunca re-serializar el JSON para validarla.

## 5. Recepción y procesamiento (paquete `whatsapp`)

Ubicación según la convención de subpaquetes de `CLAUDE.md`:

| Clase | Ubicación | Responsabilidad |
| --- | --- | --- |
| `WhatsAppWebhookResource` | `whatsapp.web` | Implementa la interfaz generada del tag `Webhooks` (sin `@IfBuildProfile`: existe en producción) |
| `WebhookSignatureFilter` | `whatsapp.web` | Validación HMAC (sección 4) |
| `InboundMessage`, `InboundEcho` | `whatsapp.model` | Records de dominio: `messageId`, `from`, `type`, `text`, `timestamp` / `messageId`, `to` |
| `WebhookMapper` | `whatsapp.mapper` | MapStruct: DTOs generados (`api.model.WhatsApp*`) → `InboundMessage` / `InboundEcho` |
| `MessageDeduplicator` | `whatsapp.services` | Caffeine con `expireAfterWrite(dedupe-ttl)`; `boolean firstTime(String messageId)` |
| `InboundDispatcher` | `whatsapp.services` | Encola el trabajo en orden por celular |
| `InboundMessageProcessor` | `whatsapp.services` | Procesa un mensaje: filtro, `ChatService`, envío |
| `OutboundMessenger` | `whatsapp.repository` | Puerto para enviar texto |
| `CloudApiMessenger`, `GraphApiClient` | `whatsapp.repository` | Adaptador de la Cloud API y su REST Client (`@RegisterRestClient(configKey = "whatsapp-graph")`) |
| `HmacUtils` | `whatsapp.utils` | Cálculo y comparación de la firma, sin estado |
| `WhatsAppConfig` | `whatsapp.config` | `@ConfigMapping(prefix = "whatsapp")` |

### 5.1 `receiveWhatsAppEvents`

1. Mapea el payload con `WebhookMapper`.
2. Descarta los `changes` cuyo `metadata.phone_number_id` no sea el configurado (registra un warning).
3. Por cada `messages[]`: si `MessageDeduplicator.firstTime(id)`, lo pasa a `InboundDispatcher`.
4. Por cada `message_echoes[]` (`field = smb_message_echoes`): `ConversationPauseRegistry.pause(to, bot.pause.duration)`. Es la dueña respondiendo a mano desde su app.
5. `statuses[]` se ignoran.
6. Responde 200 **sin esperar** el procesamiento. Objetivo: menos de 500 ms. Meta reintenta si no recibe 200 a tiempo; por eso existe la deduplicación.
7. Si el payload no se puede deserializar: responde 200 igual (para que Meta no reintente algo que nunca va a funcionar) y registra un error sin el cuerpo.

### 5.2 `InboundDispatcher`

- Los mensajes de un **mismo celular** se procesan en orden y de a uno, porque `TurnContext` y la memoria lo asumen. Los de celulares distintos, en paralelo.
- Implementación sugerida: un `ConcurrentHashMap<String, CompletableFuture<Void>>` que encadena cada mensaje al anterior del mismo celular, ejecutando en un executor de hilos virtuales. Limpia la entrada cuando la cadena termina.
- Una excepción en un mensaje no debe cortar la cadena de los siguientes.

### 5.3 `InboundMessageProcessor`

1. Si `type != "text"`: no responde nada (la dueña lo ve en su app) y registra el tipo.
2. Llama a `ChatService.reply(from, text)`.
3. Si `ChatReply.text()` está vacío (pausado o celular inválido): no envía nada.
4. Si no: trunca a `max-text-length` si hiciera falta y envía con `OutboundMessenger.sendText(from, text)`.
5. Registra una traza: id de mensaje, celular enmascarado, tools usadas, guardia, latencia total y resultado del envío.

### 5.4 `OutboundMessenger` y `CloudApiMessenger`

```java
public interface OutboundMessenger {
    void sendText(String to, String body);
}
```

- `CloudApiMessenger` usa `GraphApiClient`: `POST /{api-version}/{phone-number-id}/messages` con `Authorization: Bearer <access-token>` y el cuerpo:

```json
{ "messaging_product": "whatsapp", "recipient_type": "individual", "to": "51911111111",
  "type": "text", "text": { "preview_url": false, "body": "..." } }
```

- Errores 4xx: no reintenta; registra el código de error de Meta (`error.code`) y el celular enmascarado.
- Errores 5xx o timeout: un reintento tras 2 s (`@Retry` de Fault Tolerance, solo para esas excepciones).
- La interfaz existe para que la Fase 4 pueda agregar otra implementación si se usa un proveedor (BSP) en lugar de la Cloud API directa.

## 6. Tests

**Unitarios**

- `WebhookSignatureFilterTest`: firma válida, inválida, header ausente, y cuerpo con caracteres no ASCII (tildes y emojis).
- `MessageDeduplicatorTest` con `Ticker` controlable.
- `InboundDispatcherTest`: 3 mensajes del mismo celular se procesan en orden aunque el primero tarde más; 2 celulares distintos en paralelo; una excepción no corta la cadena.
- `InboundMessageProcessorTest`: imagen (no envía), pausado (no envía), texto normal (envía), texto largo (trunca).
- `WebhookMapperTest` con los 6 fixtures.

**Componente (`@QuarkusTest`)**, con `@InjectMock ChatService` y `@InjectMock OutboundMessenger`:

- `GET` con token correcto devuelve el challenge; con token incorrecto, 403.
- `POST` sin firma → 401.
- `POST text-message.json` firmado → 200; con Awaitility, `sendText("51911111111", ...)` se llama una vez.
- El mismo payload enviado 2 veces → `sendText` se llama una sola vez.
- `echo-message.json` → el celular queda pausado y no se envía nada.
- `image-message.json`, `status-update.json` y `other-phone-number-id.json` → no se envía nada.
- `two-messages.json` → 2 envíos, en orden.

Para firmar en los tests, crea un helper que calcule el HMAC con el `app-secret` del perfil `test`.

## 7. Prueba de punta a punta con el número de prueba

1. Levanta la app: `./mvnw quarkus:dev`, con todas las variables de entorno (Sheet, Foundry y WhatsApp).
2. Expón el puerto 8080 con un túnel HTTPS: `cloudflared tunnel --url http://localhost:8080 --http-host-header localhost:8080` (`brew install cloudflared`). Sin `--http-host-header`, Quarkus en modo dev responde 400 vacío porque el `Host` no es `localhost`. La URL del túnel rápido cambia cada vez que se reinicia; hay que volver a verificar el webhook en Meta. Comprueba el túnel antes de ir a Meta: `curl "https://<túnel>/api/v1/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=$WHATSAPP_VERIFY_TOKEN&hub.challenge=ok"` debe responder `ok`.
3. En developers.facebook.com: tu app → **Conectar en WhatsApp** → **Configuración básica** → **Paso 2. Configuración de producción** → **Configurar Webhooks** (aunque se use el número de prueba). URL de devolución de llamada `https://<túnel>/api/v1/webhooks/whatsapp`, "Identificador de verificación" = `WHATSAPP_VERIFY_TOKEN`, sin certificado de cliente. Verificar y guardar.
4. Suscribe el campo `messages`.
5. En **Paso 1. Probar**: genera el identificador de acceso (`WHATSAPP_ACCESS_TOKEN`, vence en ~24 h; reinicia la app tras exportarlo), toma el Phone Number ID y registra tu celular como **Destinatario**.
6. Desde tu celular, escribe al número de prueba: "hola cuanto era lo mio?".
7. Comprueba:
    - Que llegue la respuesta.
    - Que un segundo mensaje no repita la presentación.
    - Que una pregunta fuera de alcance pause al bot.
    - Que un audio no reciba respuesta.

Para que tu celular tenga datos, regístralo temporalmente en la pestaña Clientas con algún pedido de prueba, y bórralo al terminar.

### Lo que se aprendió al probar

- **App sin publicar**: el panel avisa que, mientras la app no esté publicada, solo recibe los webhooks de prueba enviados desde el panel, no mensajes reales. El webhook de prueba del panel trae un `phone_number_id` ficticio, así que el bot lo descarta (`Cambio descartado: phone_number_id distinto al configurado`): eso confirma túnel, firma y deserialización, pero no el procesamiento.
- **Ventana de 24 h**: WhatsApp solo entrega texto libre si la clienta escribió al negocio en las últimas 24 h. La Cloud API responde 200 (`send=OK`) aunque la entrega falle después (código 131047, informado en un webhook `statuses`). Antes de simular un mensaje, escribe desde tu celular al número de prueba para abrir la ventana.
- **Errores de envío frecuentes** (en el log, `Envío rechazado por Meta … error.code=`): `131030` destinatario no registrado; `190` token vencido.

### Simular el mensaje entrante con Postman

Sirve mientras la app no esté publicada. La respuesta sí sale por la Cloud API hacia tu celular.

- Environment: `baseUrl` (`http://localhost:8080`), `phoneNumberId`, `myPhone` (`51` + 9 dígitos) y `appSecret` (tipo *secret*, con *Current value*; no exportarlo con valor).
- Request: `POST {{baseUrl}}/api/v1/webhooks/whatsapp`, `Content-Type: application/json`, body *raw / JSON* igual a `text-message.json` con `"phone_number_id": "{{phoneNumberId}}"`, `"from"` y `"wa_id"` = `"{{myPhone}}"` e `"id": "{{messageId}}"`.
- Script pre-request (firma los bytes que se envían, ya con las variables resueltas):

```js
pm.variables.set('messageId', 'wamid.postman-' + Date.now());

const secret = pm.environment.get('appSecret');
if (!secret) {
    throw new Error('appSecret vacío: selecciona el environment y revisa el Current value');
}
if (!pm.request.body || pm.request.body.mode !== 'raw') {
    throw new Error('El body debe ser raw / JSON');
}

const raw = pm.variables.replaceIn(pm.request.body.raw);
const signature = CryptoJS.HmacSHA256(raw, secret).toString(CryptoJS.enc.Hex);
pm.request.headers.upsert({ key: 'X-Hub-Signature-256', value: 'sha256=' + signature });
```

- Variantes: `"type": "audio"` (sin respuesta); id fijo enviado dos veces (una sola respuesta); `field: smb_message_echoes` con `message_echoes: [{ "from": "<número del negocio>", "to": "{{myPhone}}", ... }]` (pausa 12 h; se quita con `DELETE {{baseUrl}}/api/v1/conversations/{{myPhone}}/pause`).

## Criterios de aceptación

- [ ] `./mvnw verify` pasa y el lint de Spectral no tiene errores.
- [ ] Firma inválida → 401; el mismo mensaje reenviado se responde una sola vez.
- [ ] Un eco de la dueña pausa al bot en ese chat durante 12 h (test de componente).
- [ ] Audios, imágenes y stickers no reciben respuesta automática.
- [ ] Los mensajes del mismo celular se responden en orden.
- [ ] El webhook responde en menos de 500 ms (medido en el test de componente con el `ChatService` mockeado, que tarda 3 s).
- [ ] Prueba de punta a punta con el número de prueba completada (sección 7).
- [ ] No se registran en logs el cuerpo de los mensajes ni celulares completos.
