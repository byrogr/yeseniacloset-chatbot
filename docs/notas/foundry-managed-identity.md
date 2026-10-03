# Foundry con Managed Identity: no soportado por la extensión

Revisado en la Fase 4 (parte C4), con `quarkus-langchain4j-azure-openai` 1.13.3 (Quarkus 3.40.1).

## Qué ofrece la extensión

Para autenticarse contra Azure OpenAI / Microsoft Foundry, la extensión solo tiene dos propiedades:

| Propiedad | Qué hace |
| --- | --- |
| `quarkus.langchain4j.azure-openai.api-key` | Envía la API key del recurso |
| `quarkus.langchain4j.azure-openai.ad-token` | Envía un token de Entra ID **fijo** en el header `Authorization` |

No hay integración con `DefaultAzureCredential` ni con `ManagedIdentityCredential`: la extensión no obtiene ni renueva tokens. Un token de Entra ID vence en cerca de una hora, así que pasarlo por `ad-token` dejaría de funcionar poco después de cada arranque.

## Decisión

Se mantiene la API key como secreto de la Container App (`FOUNDRY_API_KEY`, ver `docs/operacion/variables-entorno.md`).

Los pasos 1 y 2 de la parte C4 (identidad asignada por el sistema y rol **Cognitive Services OpenAI User**) pueden hacerse igual: no afectan al bot y dejan la identidad lista para cuando haya soporte.

## Cuándo revisarlo

- Si una versión nueva de `quarkus-langchain4j-azure-openai` agrega autenticación con Entra ID o Managed Identity (revisar las notas de versión al actualizar Quarkus).
- Alternativa sin esperar a la extensión: un `ModelAuthProvider` propio que pida el token a la identidad de la Container App y lo renueve antes de que venza. Agrega dependencias (`azure-identity`) y código de infraestructura; solo vale la pena si rotar la API key se vuelve un problema.

Mientras tanto, rotar la API key cuando se sospeche una filtración: Foundry → recurso → Claves y punto de conexión → regenerar la clave que no está en uso, actualizar el secreto de la Container App y luego regenerar la otra.
