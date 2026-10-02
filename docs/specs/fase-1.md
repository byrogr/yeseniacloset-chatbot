# Fase 1 — Lectura del Sheet y dominio

## Objetivo

Dado un celular, obtener el `EstadoCuenta` correcto de esa clienta leyendo el Google Sheet. Sin LLM ni WhatsApp.

## Alcance

**Incluye**

1. Crear el proyecto Quarkus si no existe.
2. Paquete `sheets`: selección de pestañas, lectura con la API, parseo y caché.
3. Paquete `cuenta`: construcción del `EstadoCuenta`.
4. Endpoint de desarrollo `GET /dev/estado-cuenta/{celular}`, solo en perfil `dev`.
5. Tests unitarios y de componente con el fixture `src/test/resources/fixtures/workbook-fase1.json`.

**No incluye**: LangChain4j, WhatsApp, Dockerfile, despliegue, CI. No agregar esas dependencias todavía.

## 1. Proyecto

- Si no hay `pom.xml`, créalo con el plugin de Maven de Quarkus (última versión 3.x estable):
  - groupId `pe.rmsolutions`, artifactId `chatbot-pedidos`, paquete base `pe.rmsolutions.chatbot`.
  - Extensiones: `rest-jackson`, `smallrye-health`.
  - Incluir Maven wrapper.
- Dependencias adicionales:
  - `com.google.apis:google-api-services-sheets` (versión v4 más reciente).
  - `com.google.auth:google-auth-library-oauth2-http`.
  - Test: `org.assertj:assertj-core`, `io.rest-assured:rest-assured`.
- Eliminar el recurso y los tests de ejemplo que genera el arquetipo.

## 2. Configuración

```properties
sheets.spreadsheet-id=${SHEETS_SPREADSHEET_ID}
sheets.credentials-file=${SHEETS_CREDENTIALS_FILE:}
sheets.credentials-json=${SHEETS_CREDENTIALS_JSON:}
sheets.cache-ttl=60s
sheets.stale-max=30m
```

- Mapear con `@ConfigMapping(prefix = "sheets")` en la interfaz `SheetsConfig`.
- Credenciales: si `credentials-json` tiene valor, se usa (producción, desde un secreto); si no, `credentials-file` (desarrollo). Si ambos están vacíos, falla al iniciar con un mensaje claro.
- En el perfil `test`, usar valores ficticios y no tocar la red.

## 3. Paquete `sheets`

### 3.1 `TabNamePolicy`

- `boolean esCampania(String titulo)`: true si cumple `^C-(\d+)-(pacifika|carmel)$`, sin distinguir mayúsculas y con `trim`.
- `Optional<CampaniaId> parse(String titulo)`: devuelve `CampaniaId(String titulo, int numero, String catalogo)`, con `catalogo` en minúsculas y `titulo` tal como viene del Sheet.
- Constante `CLIENTAS = "Clientas"`.

### 3.2 `WorkbookSource` (interfaz)

```java
public interface WorkbookSource {
    RawWorkbook leer();
}

public record RawWorkbook(Map<String, List<List<Object>>> valores) {}
```

`valores` contiene solo `Clientas` y las pestañas de campaña, con su clave igual al título de la pestaña.

**`GoogleSheetsWorkbookSource`** (implementación por defecto, `@ApplicationScoped`):

1. `spreadsheets.get` con `fields=sheets.properties.title` para listar las pestañas.
2. Filtra con `TabNamePolicy` y agrega `Clientas`.
3. Una sola llamada `values.batchGet` con rangos `'<titulo>'!A1:M` (comillas simples en el nombre) y `Clientas!A1:B`, con `valueRenderOption=UNFORMATTED_VALUE` y `dateTimeRenderOption=FORMATTED_STRING`.
4. Scope: `https://www.googleapis.com/auth/spreadsheets.readonly`.

Importante: la API **omite las celdas vacías al final de cada fila**. Una fila puede tener menos columnas que el encabezado, y una fila totalmente vacía puede llegar como lista vacía.

**`FixtureWorkbookSource`** (solo en `src/test`): lee el JSON del fixture, aplica la misma `TabNamePolicy` y devuelve el `RawWorkbook`. En los tests de componente reemplaza a la implementación de Google con `@Alternative` + `@Priority` o `QuarkusMock`.

### 3.3 `WorkbookParser`

```java
public ParsedWorkbook parse(RawWorkbook raw)

public record ParsedWorkbook(
    List<Clienta> clientas,
    List<FilaPedido> filas,
    List<String> advertencias) {}

public record Clienta(String nombre, String celular) {}

public record FilaPedido(
    CampaniaId campania, int filaSheet, String producto, String talla,
    String clienta, String para, LocalDate fechaPago, Estado estado,
    BigDecimal montoTotal) {}
```

Reglas:

- **Encabezados:** se leen de la fila 1 y se mapean por nombre, con `trim`, sin distinguir mayúsculas ni tildes. Si a una pestaña de campaña le falta alguno de `Producto`, `Clienta`, `Estado`, `Monto total` o `Fecha de pago`, se omite la pestaña entera y se agrega una advertencia.
- **Filas:** `filaSheet` es el número real de fila (encabezado = 1). Una fila con `Producto` vacío se descarta sin advertencia, porque son restos de la plantilla.
- **Celdas:** una celda ausente (fila corta) o un `""` cuenta como vacía. Los números pueden llegar como `Integer`, `Long`, `Double` o `String`; se convierten a `BigDecimal` con `new BigDecimal(valor.toString())` y escala 2 `HALF_UP`. Un texto no numérico en `Monto total` se trata como vacío y genera advertencia.
- **Fechas:** formato `dd/MM/yyyy`. Vacía = `null`. Si no se puede parsear, `null` con advertencia.
- **`Estado`:** enum `PENDIENTE, ENTREGADO, PAGADO, AGOTADO, CANCELADO`, comparado con `trim` y sin distinguir mayúsculas. Un valor desconocido descarta la fila con advertencia.
- **`Clienta`:** se resuelve contra la pestaña Clientas con `NombreNormalizer`. Si no existe, se descarta la fila con advertencia. En `FilaPedido.clienta` se guarda el nombre **tal como está en Clientas**.
- **`Para`:** `trim`; vacío = `null`.
- **Celulares** de Clientas: se normalizan con `CelularNormalizer`. Un celular inválido descarta la clienta con advertencia.
- **Fecha de pago distinta** dentro de la misma clienta y campaña, considerando solo filas cobrables (Pendiente o Entregado): advertencia. La regla de qué fecha se usa vive en `cuenta`.
- **Formato de advertencias:** `"<pestaña> fila <n>: <motivo>"`, o `"<pestaña>: <motivo>"` si aplica a la pestaña entera.

### 3.4 Normalizadores (clases utilitarias, en `sheets`)

- `NombreNormalizer.clave(String)`: `trim`, colapsa espacios internos, quita tildes (`Normalizer.Form.NFD` y eliminar marcas diacríticas), minúsculas, y quita el punto final. Así, `"MARIALE R."`, `"mariale r"` y `" Mariale  R. "` → `"mariale r"`; `"Jose Perez"` y `"José Pérez"` → `"jose perez"`.
- `CelularNormalizer.normalizar(String)`: deja solo dígitos; si quedan 9 dígitos que empiezan con `9`, antepone `51`. Válido si cumple `^51\d{9}$`; si no, `Optional.empty()`.

### 3.5 `WorkbookProvider` (caché)

```java
public ParsedWorkbook obtener()
```

- Guarda el último `ParsedWorkbook` válido y su instante de carga, en un `AtomicReference`.
- Si la carga tiene menos de `cache-ttl`, la devuelve sin llamar a la API.
- Si venció, recarga. Si la recarga falla y la última carga válida tiene menos de `stale-max`, la devuelve y registra un warning. Si no hay carga válida o supera `stale-max`, lanza `WorkbookNoDisponibleException`.
- Las advertencias de `ParsedWorkbook` se registran **una vez por recarga**, no por consulta.
- Usa `Clock` inyectado (producer `@ApplicationScoped` con `Clock.systemDefaultZone()`).
- Las recargas concurrentes no deben disparar varias llamadas simultáneas a la API (por ejemplo, con un `ReentrantLock`).

## 4. Paquete `cuenta`

### 4.1 Modelo de salida

```java
public record EstadoCuenta(
    boolean registrada, String clienta,
    List<CampaniaCuenta> campanias, BigDecimal totalGeneral) {}

public record CampaniaCuenta(
    String campania, int numero, String catalogo, LocalDate fechaPago,
    List<GrupoPara> grupos, List<PrendaInfo> agotadas,
    List<PrendaInfo> sinPrecio, BigDecimal total) {}

public record GrupoPara(String para, List<Prenda> prendas, BigDecimal subtotal) {}

public record Prenda(String producto, String talla, BigDecimal monto, Estado estado) {}

public record PrendaInfo(String producto, String talla, String para) {}
```

`fechaPago` se serializa como `dd/MM/yyyy`, o `null` si falta. Los montos se serializan como número con 2 decimales.

### 4.2 `EstadoCuentaService`

```java
public EstadoCuenta consultar(String celularCrudo)
```

1. Normaliza el celular. Si es inválido o no existe en Clientas: `EstadoCuenta(false, null, List.of(), 0.00)`.
2. Toma las filas de esa clienta y las agrupa por campaña.
3. Una campaña **se incluye solo si tiene al menos una fila cobrable** (Pendiente o Entregado).
4. Dentro de una campaña incluida:
    - Fila cobrable **con** `montoTotal`: va a `grupos`, en el grupo de su `Para`.
    - Fila cobrable **sin** `montoTotal`: va a `sinPrecio` y no suma.
    - Fila `Agotado`: va a `agotadas`.
    - `Pagado` y `Cancelado`: se ignoran.
5. **Grupos:** se agrupan por `NombreNormalizer.clave(para)`. Se muestra el `para` de la primera fila del grupo, en orden de fila. El grupo propio (`para = null`) va primero y el resto en orden alfabético. Un grupo sin prendas no aparece.
6. **`fechaPago`:** la más temprana entre las filas cobrables de la campaña, o `null` si ninguna tiene fecha.
7. **Totales:** subtotal = suma de los montos del grupo; total de campaña = suma de subtotales; `totalGeneral` = suma de totales de campaña.
8. **Orden de campañas:** por `numero` ascendente y luego por `catalogo` alfabético.
9. Si la clienta está registrada pero no tiene campañas incluidas: `registrada = true` y lista vacía.

Si `WorkbookProvider` lanza `WorkbookNoDisponibleException`, el servicio la propaga. La Fase 2 decide cómo responder.

## 5. Endpoint de desarrollo (`ops`)

- `GET /dev/estado-cuenta/{celular}` → `200` con `EstadoCuenta` en JSON.
- Si el libro no está disponible → `503`.
- Activo solo en perfil `dev`: `@IfBuildProfile("dev")`. No debe existir en el build de producción.
- Health: el `ReadinessCheck` de `sheets` reporta DOWN solo si no hay ninguna carga válida y la última recarga falló.

## 6. Tests

### 6.1 Unitarios (sin Quarkus)

- `TabNamePolicyTest`: válidos (`C-11-pacifika`, `C-12-Carmel`, `c-3-PACIFIKA`, `" C-11-carmel "`) e inválidos (`PLANTILLA`, `Resumen`, `C11-pacifika`, `C-11-otro`, `Campaña 3 Pacifika`).
- `NombreNormalizerTest` y `CelularNormalizerTest`: los casos de la sección 3.4, más `"+51 911 111 111"`, `"911111111"`, `"51 966-666-666"` e inválidos (`"12345"`, `"811111111"`).
- `WorkbookParserTest` con el fixture: advertencias esperadas (sección 6.3), filas descartadas y fila corta (`Chompa`) parseada con monto `null`.
- `EstadoCuentaServiceTest` con el fixture: todos los casos de la sección 6.2.
- `WorkbookProviderTest` con `Clock` controlable y una `WorkbookSource` falsa: dentro del TTL no recarga; vencido recarga; falla con datos de menos de 30 min devuelve los anteriores; falla sin datos lanza excepción.

### 6.2 Resultados esperados con el fixture

| Celular de entrada | Resultado esperado |
| --- | --- |
| `51911111111` (Gaby) | 2 campañas. **C-11-carmel:** fechaPago 20/10/2026; grupo propio [Pantalón 59.90, Blusa crema 19.90], subtotal 79.80; total 79.80. **C-11-pacifika:** fechaPago 15/10/2026; grupo propio [Blusa blanca 19.90], subtotal 19.90; grupo "Ana" [Vestido lila 35.91, Ropa de baño 39.92], subtotal 75.83; agotadas [Vestido azul S]; sinPrecio []; total 95.73. totalGeneral **175.53**. Sin grupo "Lucía" (pagado) ni "Cartera" (estado desconocido). C-10-pacifika no aparece (todo pagado). |
| `+51 911 111 111` | Igual que el anterior. |
| `51933333333` (Roger) | 2 campañas. **C-11-pacifika:** fechaPago 15/10/2026; grupo propio [Conjunto negro 71.91]; sinPrecio [Chompa M]; total 71.91 (Top cancelado no aparece). **C-12-Carmel:** fechaPago `null`; grupo propio [Bufanda 15.00]; total 15.00. totalGeneral **86.91**. C-10-pacifika no aparece aunque tenga un agotado. |
| `51922222222` (Mariale R.) | 1 campaña. **C-11-pacifika:** fechaPago 15/10/2026; grupo propio [Polo rosa 25.00] (escrito "MARIALE R." en el Sheet); sinPrecio [Enterizo negro S]; total 25.00. totalGeneral **25.00**. C-11-carmel no aparece (pagado). |
| `51944444444` (José Pérez) | 1 campaña, C-11-pacifika: [Casaca 89.91] (escrito "Jose Perez" en el Sheet). totalGeneral **89.91**. |
| `51955555555` (Lucía) | `registrada = true`, sin campañas, totalGeneral 0.00. Las prendas "Para: Lucía" de Gaby **no** son de esta clienta. |
| `966666666` (Ana Torres) | `registrada = true`, sin campañas (celular guardado como `51 966-666-666`). |
| `51999999999` | `registrada = false`. |
| `12345` | `registrada = false`. |

### 6.3 Advertencias esperadas del fixture (exactamente 3)

1. `C-11-pacifika fila 12`: clienta "Desconocida" no está en Clientas.
2. `C-11-pacifika fila 13`: estado "Reservado" desconocido.
3. `C-11-carmel`: Gaby tiene fechas de pago distintas (20/10/2026 y 25/10/2026).

Basta con que el texto contenga la pestaña, la fila (cuando aplica) y el dato problemático; no hace falta comparar la frase exacta.

### 6.4 Componente (`@QuarkusTest`)

- Con `FixtureWorkbookSource` activo en el perfil de test, `GET /dev/estado-cuenta/51911111111` devuelve 200 y `totalGeneral = 175.53`. Si `@IfBuildProfile("dev")` impide probarlo en `test`, probar el servicio inyectado en su lugar y documentarlo.

## 7. Verificación manual con el Sheet real

1. `export SHEETS_SPREADSHEET_ID=...` y `export SHEETS_CREDENTIALS_FILE=...`
2. `./mvnw quarkus:dev`
3. `curl localhost:8080/dev/estado-cuenta/<celular de una clienta real>`
4. Comparar con el Sheet a mano.

## 8. Build nativo (exploratorio, no bloqueante)

Intentar `./mvnw package -Dnative` (con `-Dquarkus.native.container-build=true` si no hay GraalVM). Si la librería de Google falla en nativo, **no** resolverlo en esta fase: documentar el error en `docs/notas/nativo-google.md` con la salida relevante.

## Criterios de aceptación

- [ ] `./mvnw verify` pasa sin tests omitidos.
- [ ] Todos los casos de la sección 6.2 pasan, con montos exactos al céntimo.
- [ ] El parser produce exactamente las 3 advertencias de la sección 6.3.
- [ ] Cobertura de líneas de `sheets` y `cuenta` de al menos 80 % (agregar `quarkus-jacoco` en test).
- [ ] Ningún secreto ni ID real en el repositorio; `application.properties` solo referencia variables de entorno.
- [ ] Con el Sheet real, el endpoint de desarrollo devuelve el estado de cuenta de una clienta y coincide con la suma manual.
- [ ] Resultado del build nativo documentado (éxito o nota de error).
