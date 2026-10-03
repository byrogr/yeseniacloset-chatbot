package pe.rmsolutions.chatbot.agent.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Pattern;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Configuración del asistente (prefijo {@code bot}).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ConfigMapping(prefix = "bot")
public interface BotConfig {

    String ownerName();

    /**
     * Interruptor de apagado. Apagado, ningún mensaje llega al modelo; los ecos siguen pausando.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Celulares del piloto ({@code 51} + 9 dígitos). Vacío = todas las clientas.
     */
    Optional<List<@Pattern(regexp = "^51\\d{9}$", message = "debe ser 51 + 9 dígitos") String>> allowlist();

    /**
     * Celular personal de la dueña ({@code 51} + 9 dígitos) al que se deriva a las clientas cuando el número
     * del bot no lo atiende nadie a mano. Vacío = modo coexistencia: la dueña responde en el mismo chat y la
     * derivación pausa al bot.
     */
    Optional<@Pattern(regexp = "^51\\d{9}$", message = "debe ser 51 + 9 dígitos") String> ownerContactPhone();

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
