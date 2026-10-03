package pe.rmsolutions.chatbot.agent.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

/**
 * Configuración del asistente (prefijo {@code bot}).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ConfigMapping(prefix = "bot")
public interface BotConfig {

    String ownerName();

    Memory memory();

    Pause pause();

    /**
     * Memoria de conversación por celular.
     *
     * @author Roger Rojas - roger.rojas@rmsolutions.pe
     */
    interface Memory {
        @WithDefault("10")
        int maxMessages();

        @WithDefault("30m")
        Duration ttl();
    }

    /**
     * Pausa del asistente tras derivar a la dueña.
     *
     * @author Roger Rojas - roger.rojas@rmsolutions.pe
     */
    interface Pause {
        @WithDefault("12h")
        Duration duration();
    }
}
