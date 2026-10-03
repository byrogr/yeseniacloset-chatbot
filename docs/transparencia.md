# Asistente automático de pedidos: nota de transparencia

> Borrador para revisar con la dueña antes de publicarlo. Lo que está entre corchetes `[...]` se completa con ella.

Esta nota explica, en palabras sencillas, qué hace el asistente automático de WhatsApp de [nombre de la tienda], cómo usa tus datos y qué límites tiene.

## Qué hace el asistente

El asistente responde, por WhatsApp, solo tres cosas sobre **tus** pedidos de catálogo (Pacifika y Carmel):

- qué prendas pediste,
- cuánto es el total de tu pedido,
- hasta cuándo puedes pagar.

Es un programa automático que usa inteligencia artificial (IA). No es una persona.

Funciona en un **número de WhatsApp propio, solo para consultas de pedidos**. Ese número no lo lee nadie a mano.

## Qué no hace

El asistente **no** toma pedidos, no agrega ni cancela prendas, no confirma pagos, no cambia fechas de pago, no da descuentos y no responde sobre tallas, stock, catálogos nuevos ni entregas.

Todo eso lo atiende directamente [nombre de la dueña] en su número de siempre. Cuando preguntas algo de esto, el asistente te responde con un enlace para escribirle a ella.

## Cómo se calculan los montos

Los montos **no los calcula la IA**. Salen siempre de la hoja donde [nombre de la dueña] registra los pedidos y los calcula un programa común, sin IA. La IA solo redacta la respuesta.

Antes de enviarte cualquier respuesta, una verificación automática compara cada monto con los de la hoja. Si la IA escribió un monto que no coincide, esa respuesta se descarta y recibes en su lugar un resumen armado directamente desde la hoja.

Si ya hiciste un pago o adelanto, el total que ves todavía no lo descuenta: eso lo hace [nombre de la dueña].

## Qué datos usa

- **Tu número de celular**, para saber quién eres y buscar solo tus pedidos. El asistente nunca muestra pedidos de otras personas.
- **El texto de tu mensaje**, para entender qué preguntas.
- **Tus pedidos registrados en la hoja**: prendas, tallas, precios, estados y fechas de pago.

## Dónde se procesan

- **Meta (WhatsApp)** recibe y entrega los mensajes, como en cualquier chat de WhatsApp.
- **Microsoft Azure** aloja el programa del asistente y el modelo de IA (Microsoft Foundry) que redacta las respuestas.
- **Google** guarda la hoja de pedidos. El asistente solo la lee, nunca la modifica.

Sobre el modelo de IA, según la documentación de Microsoft ([Data, privacy, and security for Foundry Models sold by Azure](https://learn.microsoft.com/en-us/azure/foundry/responsible-ai/openai/data-privacy)):

- Los mensajes y las respuestas **no** se usan para entrenar modelos de IA sin permiso, y **no** están disponibles para OpenAI ni para otros clientes.
- Microsoft revisa automáticamente los mensajes para detectar contenido dañino o usos indebidos. Si su sistema marca un mensaje como posible abuso, personal autorizado de Microsoft puede revisarlo, con acceso controlado ([abuse monitoring](https://learn.microsoft.com/en-us/azure/foundry/openai/concepts/abuse-monitoring)). Para esa revisión, Microsoft puede guardar esos mensajes en la región de Azure donde está el servicio.

## Qué se guarda

- El asistente **no guarda** el texto de tus mensajes.
- Para entender la conversación, recuerda tus últimos mensajes durante **30 minutos**. Pasado ese tiempo, o si el programa se reinicia, los olvida.
- Los registros técnicos del programa no incluyen el texto de los mensajes ni tu número completo (se ve así: `51987***321`).

## Límites conocidos

- Puede no entender mensajes ambiguos o con muchas faltas. Si no te entiende, escríbele a [nombre de la dueña].
- No escucha audios ni ve fotos. Si envías uno, te avisará que solo lee texto.
- Responde con lo que está en la hoja. Si la hoja todavía no está actualizada (por ejemplo, un pedido recién hecho), la respuesta tampoco lo estará.

## Supervisión humana

- Para hablar con una persona, escríbele a [nombre de la dueña] a su número de siempre. Cada vez que preguntas algo que el asistente no puede resolver, te comparte el enlace a su chat.
- [Nombre de la dueña] puede apagar el asistente en cualquier momento. Apagado, el número solo responde con el enlace para escribirle a ella.

## Contacto

Usar el asistente es opcional. Si no quieres usarlo, no le escribas a su número: [nombre de la dueña] te sigue atendiendo como siempre en el suyo.

Si tienes dudas sobre el asistente o sobre tus datos, escríbele a [nombre de la dueña] al [número o correo de contacto].

---

Última actualización: [fecha]
