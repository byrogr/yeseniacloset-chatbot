package pe.rmsolutions.chatbot.whatsapp.services;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la deduplicación de mensajes con un {@link Ticker} controlable.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class MessageDeduplicatorTest {

    private final AtomicLong nanos = new AtomicLong();
    private final MessageDeduplicator deduplicator = new MessageDeduplicator(Duration.ofHours(24), nanos::get);

    @Test
    void soloLaPrimeraVezDevuelveTrue() {
        assertThat(deduplicator.firstTime("wamid.1")).isTrue();
        assertThat(deduplicator.firstTime("wamid.1")).isFalse();
        assertThat(deduplicator.firstTime("wamid.2")).isTrue();
    }

    @Test
    void dentroDelTtlSigueDuplicado() {
        deduplicator.firstTime("wamid.1");
        advance(Duration.ofHours(23).plusMinutes(59));

        assertThat(deduplicator.firstTime("wamid.1")).isFalse();
    }

    @Test
    void trasElTtlVuelveASerNuevo() {
        deduplicator.firstTime("wamid.1");
        advance(Duration.ofHours(24).plusSeconds(1));

        assertThat(deduplicator.firstTime("wamid.1")).isTrue();
    }

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }
}
