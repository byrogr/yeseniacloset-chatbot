package pe.rmsolutions.chatbot.whatsapp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

/**
 * Configuración del canal de WhatsApp Cloud API (prefijo {@code whatsapp}). Los secretos llegan por
 * variables de entorno.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ConfigMapping(prefix = "whatsapp")
public interface WhatsAppConfig {

    String apiBaseUrl();

    String apiVersion();

    String phoneNumberId();

    String accessToken();

    String appSecret();

    String verifyToken();

    @WithDefault("24h")
    Duration dedupeTtl();

    @WithDefault("4096")
    int maxTextLength();
}
