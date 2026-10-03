package pe.rmsolutions.chatbot.agent;

import pe.rmsolutions.chatbot.agent.config.BotConfig;

import java.time.Duration;

/**
 * {@link BotConfig} fijo para tests unitarios sin CDI.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public final class TestBotConfig implements BotConfig {

    public static final Duration PAUSE = Duration.ofHours(12);

    @Override
    public String ownerName() {
        return "Yesenia";
    }

    @Override
    public Memory memory() {
        return new Memory() {
            @Override
            public int maxMessages() {
                return 10;
            }

            @Override
            public Duration ttl() {
                return Duration.ofMinutes(30);
            }
        };
    }

    @Override
    public Pause pause() {
        return () -> PAUSE;
    }
}
