package pe.rmsolutions.chatbot.agent.services;

import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.agent.MutableClock;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class ConversationPauseRegistryTest {

    private static final String PHONE = "51911111111";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-02T10:00:00Z"));
    private final ConversationPauseRegistry registry = new ConversationPauseRegistry(clock);

    @Test
    void sinPausaNoEstaPausado() {
        assertThat(registry.isPaused(PHONE)).isFalse();
        assertThat(registry.pausedUntil(PHONE)).isEmpty();
    }

    @Test
    void pausaVigenteHastaQueVence() {
        registry.pause(PHONE, Duration.ofHours(12));

        assertThat(registry.isPaused(PHONE)).isTrue();
        assertThat(registry.pausedUntil(PHONE)).contains(Instant.parse("2026-10-02T22:00:00Z"));
        assertThat(registry.isPaused("51922222222")).isFalse();

        clock.advance(Duration.ofHours(12).minusSeconds(1));
        assertThat(registry.isPaused(PHONE)).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(registry.isPaused(PHONE)).isFalse();
        assertThat(registry.pausedUntil(PHONE)).isEmpty();
    }

    @Test
    void resumeQuitaLaPausaYEsIdempotente() {
        registry.pause(PHONE, Duration.ofHours(1));

        registry.resume(PHONE);
        registry.resume(PHONE);

        assertThat(registry.isPaused(PHONE)).isFalse();
    }
}
