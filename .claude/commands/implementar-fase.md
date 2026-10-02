---
description: Implementa una fase del chatbot a partir de su spec en docs/specs
argument-hint: <número de fase, p. ej. 1>
---

Vas a implementar la Fase $ARGUMENTS del chatbot de pedidos.

1. Lee completos `./CLAUDE.md` y `docs/specs/fase-$ARGUMENTS.md`. Si la spec no existe, detente y avísame.
2. **Plan primero.** Antes de escribir código, presenta:
   - Los archivos que vas a crear o modificar, agrupados por paquete.
   - El orden de implementación en pasos pequeños, cada uno verificable con tests.
   - Cualquier ambigüedad o contradicción entre la spec y `./CLAUDE.md`, con tu propuesta.
     Espera mi confirmación antes de continuar.
3. **Implementa paso a paso.** Después de cada paso, corre `./mvnw test`. No avances con tests en rojo. Si un test de la spec falla por un error en la propia spec (por ejemplo, un monto esperado mal calculado), no cambies el test para que pase: avísame.
4. **No salgas del alcance.** Nada que la spec marque como "No incluye". No agregues dependencias que la spec no mencione sin preguntarme.
5. **Cierre.** Corre `./mvnw verify` y entrega una tabla con cada criterio de aceptación de la spec: cumplido o no, y la evidencia (test, comando o archivo). Los criterios que requieren acción manual (por ejemplo, probar con el Sheet real) márcalos como pendientes y dime exactamente qué comando correr.
6. Al terminar, actualiza la sección "Estado" de `./CLAUDE.md`.
