# Comunicación a las clientas

> Borradores para que la dueña elija y ajuste. Los envía ella **desde su número personal**, no desde el número del bot. Lo que está entre corchetes `[...]` se completa antes de enviar.

## 1. Mensaje individual para las clientas del piloto

Se envía a cada clienta antes de agregar su celular a `BOT_ALLOWLIST`. Esperar su respuesta: solo se agrega si acepta.

> Hola [nombre] 😊 Te escribo porque estoy probando algo nuevo y me gustaría que me ayudes.
>
> Abrí un número de WhatsApp **solo para consultar tus pedidos**: [enlace wa.me del bot]. Ahí te responde un **asistente automático** (usa inteligencia artificial, no soy yo) que te dice qué prendas pediste, cuánto es tu total y hasta cuándo pagas, a cualquier hora.
>
> Los montos salen directo de mi hoja de pedidos, no los inventa la IA. Para todo lo demás (pedir, pagar, cambios, tallas) me sigues escribiendo a mí aquí, como siempre.
>
> Es una prueba de dos semanas con pocas clientas. ¿Te animas? Si prefieres no participar, no hay ningún problema, todo sigue igual. Aquí te explico cómo funciona y qué datos usa: [enlace a la nota de transparencia]

Variante corta, si ya conversaron antes:

> [Nombre], este es el número del asistente para consultar tus pedidos: [enlace wa.me del bot]. Es automático (usa IA). Cualquier otra cosa, me escribes a mí como siempre. Más info: [enlace a la nota de transparencia]

## 2. Anuncio general para abrirlo a todas

Por estado de WhatsApp o mensaje a las clientas, al terminar el piloto con resultado positivo, antes de vaciar `BOT_ALLOWLIST`.

> 📦 ¡Novedad! Ahora puedes consultar tus pedidos de Pacifika y Carmel cuando quieras en este número: [enlace wa.me del bot]
>
> Te responde un asistente automático (con inteligencia artificial): qué pediste, tu total y hasta cuándo pagas. Los montos salen de mi hoja de pedidos.
>
> Mi número sigue siendo el mismo para pedidos, pagos y todo lo demás 💬
>
> Cómo funciona y qué datos usa: [enlace a la nota de transparencia]

## Nota para la dueña

- El tratamiento de datos personales en Perú se rige por la **Ley 29733** (Ley de Protección de Datos Personales) y su reglamento. Si hay dudas sobre si hace falta pedir consentimiento por escrito o registrar el banco de datos, conviene consultarlo con un especialista. Estos borradores y la nota de transparencia **no son asesoría legal**.
- Si una clienta del piloto pide salir, se quita su celular de `BOT_ALLOWLIST`. A partir de ahí, si escribe al número del bot, solo recibe el enlace para escribirte a ti.
