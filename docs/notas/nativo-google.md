# Build nativo — resultado (fase 1)

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

**Resultado: éxito.** No se usó GraalVM local (no instalado); se corrió con Mandrel en
contenedor Docker (`quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`).

- Tiempo total de la imagen nativa: ~1m 1s (augmentation completa: ~2m 44s).
- Tamaño del binario: 56.38MB (53.41MB en disco).
- Sin errores relacionados con `google-api-services-sheets` ni
  `google-auth-library-oauth2-http`: ambas librerías quedaron reflejadas correctamente en el
  análisis de alcanzabilidad (12,891 tipos, 4,314 registrados para reflexión) sin excepciones
  de build.
- Advertencia menor (no bloqueante): `Option 'DynamicProxyConfigurationResources' is
  deprecated...` — ruido conocido de Quarkus/GraalVM, no afecta el resultado.

No fue necesario ningún ajuste de reflexión/proxy adicional en esta fase. Si en fases
posteriores el cliente de Google empieza a fallar en nativo (por ejemplo, al usar otras APIs
del SDK), revisar primero la configuración de reflexión de `com.google.api.client`.
