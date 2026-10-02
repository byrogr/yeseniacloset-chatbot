package pe.rmsolutions.chatbot.sheets;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.Optional;

/**
 * Configuración de acceso al Google Sheet (prefijo {@code sheets}).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ConfigMapping(prefix = "sheets")
public interface SheetsConfig {

    String spreadsheetId();

    Optional<String> credentialsFile();

    Optional<String> credentialsJson();

    @WithDefault("60s")
    Duration cacheTtl();

    @WithDefault("30m")
    Duration staleMax();
}
