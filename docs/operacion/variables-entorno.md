# Variables de entorno

Referencia para configurar la Container App `ca-chatbot-pedidos` (parte C3 de `docs/specs/fase-4.md`). Las variables **secretas** se crean como secretos de la Container App y se referencian con "Referencia a secreto". Ningún valor real va en el repositorio ni como valor por defecto en `application.yml`.

| Variable | Secreta | De dónde sale el valor | Fase |
| --- | --- | --- | --- |
| `SHEETS_SPREADSHEET_ID` | No | ID del Google Sheet del año (en la URL, entre `/d/` y `/edit`) | 1 |
| `SHEETS_CREDENTIALS_JSON` | **Sí** | Contenido completo del JSON de la service account de Google (solo lectura). En Azure se usa este en vez del archivo | 1 |
| `SHEETS_CREDENTIALS_FILE` | No | Ruta al JSON de la service account. Solo en desarrollo local; en Azure queda vacía | 1 |
| `FOUNDRY_ENDPOINT` | No | URL completa del deployment: `https://<recurso>.openai.azure.com/openai/deployments/<deployment>` | 2 |
| `FOUNDRY_API_KEY` | **Sí** | Microsoft Foundry → recurso → Claves y punto de conexión. Ver `docs/notas/foundry-managed-identity.md` | 2 |
| `FOUNDRY_DEPLOYMENT` | No | Nombre del deployment del modelo mini en Foundry | 2 |
| `FOUNDRY_API_VERSION` | No | La `api-version` que muestra el portal en el ejemplo del deployment | 2 |
| `BOT_OWNER_NAME` | No | Nombre de la dueña tal como lo verán las clientas (por ejemplo, `Yesenia`) | 2 |
| `WHATSAPP_PHONE_NUMBER_ID` | No | WhatsApp Manager → número **nuevo** del bot → Phone number ID | 3 |
| `WHATSAPP_ACCESS_TOKEN` | **Sí** | Token **permanente** del usuario de sistema (D2, paso 5). El temporal del panel vence en 24 h | 3 |
| `WHATSAPP_APP_SECRET` | **Sí** | App de Meta → Configuración de la aplicación → Básica → Clave secreta | 3 |
| `WHATSAPP_VERIFY_TOKEN` | **Sí** | Valor aleatorio largo, el mismo que se escribe en la configuración del webhook en Meta | 3 |
| `BOT_OWNER_CONTACT_PHONE` | No | Celular personal de la dueña, `51` + 9 dígitos. Las derivaciones mandan a `https://wa.me/<este número>`. Vacío = modo coexistencia | 4 |
| `BOT_ENABLED` | No | `true` o `false`. Interruptor de apagado; por defecto `true` | 4 |
| `BOT_ALLOWLIST` | No | Celulares del piloto separados por coma (`51987654321,51912345678`). Vacía = todas las clientas | 4 |
| `APPLICATIONINSIGHTS_CONNECTION_STRING` | **Sí** | Application Insights → Información general → Cadena de conexión | 4 |

Notas:

- `BOT_ALLOWLIST` y `BOT_OWNER_CONTACT_PHONE` son celulares: no se copian en issues, logs ni capturas.
- Si `BOT_OWNER_CONTACT_PHONE` o un celular de `BOT_ALLOWLIST` no cumple `51` + 9 dígitos, la aplicación no arranca y el log indica qué propiedad está mal.
- Cambiar cualquier variable en la Container App crea una revisión nueva en cerca de un minuto. Como el estado (pausas, memoria, avisos enviados) vive en memoria, se pierde en cada revisión.
