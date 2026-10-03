package pe.rmsolutions.chatbot.agent;

import pe.rmsolutions.chatbot.agent.config.BotConfig;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * {@link BotConfig} fijo para tests unitarios sin CDI.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public final class TestBotConfig implements BotConfig {

    public static final Duration PAUSE = Duration.ofHours(12);
    public static final String OWNER_CONTACT_PHONE = "51911222333";

    private final Optional<String> ownerContactPhone;
    private final boolean enabled;
    private final List<String> allowlist;

    /**
     * Modo coexistencia: sin número de contacto de la dueña, bot encendido y sin lista de piloto.
     */
    public TestBotConfig() {
        this(Optional.empty(), true, List.of());
    }

    private TestBotConfig(Optional<String> ownerContactPhone, boolean enabled, List<String> allowlist) {
        this.ownerContactPhone = ownerContactPhone;
        this.enabled = enabled;
        this.allowlist = allowlist;
    }

    /**
     * Modo número nuevo: las derivaciones mandan al chat de la dueña ({@link #OWNER_CONTACT_PHONE}).
     */
    public static TestBotConfig redirectingToOwner() {
        return new TestBotConfig(Optional.of(OWNER_CONTACT_PHONE), true, List.of());
    }

    public TestBotConfig disabled() {
        return new TestBotConfig(ownerContactPhone, false, allowlist);
    }

    public TestBotConfig withAllowlist(String... phones) {
        return new TestBotConfig(ownerContactPhone, enabled, List.of(phones));
    }

    @Override
    public String ownerName() {
        return "Yesenia";
    }

    @Override
    public Optional<String> ownerContactPhone() {
        return ownerContactPhone;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public Optional<List<String>> allowlist() {
        return allowlist.isEmpty() ? Optional.empty() : Optional.of(allowlist);
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
