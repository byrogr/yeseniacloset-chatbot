package pe.rmsolutions.chatbot.whatsapp.services;

import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.agent.MutableClock;
import pe.rmsolutions.chatbot.whatsapp.services.NoticeThrottle.Kind;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class NoticeThrottleTest {

    private static final String PHONE = "51911111111";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-03T10:00:00Z"));
    private final NoticeThrottle throttle = new NoticeThrottle(clock);

    @Test
    void unAvisoPorCelularCada12Horas() {
        assertThat(throttle.tryAcquire(Kind.NON_TEXT, PHONE)).isTrue();
        clock.advance(Duration.ofHours(11).plusMinutes(59));
        assertThat(throttle.tryAcquire(Kind.NON_TEXT, PHONE)).isFalse();
        clock.advance(Duration.ofMinutes(1));
        assertThat(throttle.tryAcquire(Kind.NON_TEXT, PHONE)).isTrue();
    }

    @Test
    void cadaTipoYCadaCelularTienenSuPropiaVentana() {
        assertThat(throttle.tryAcquire(Kind.NON_TEXT, PHONE)).isTrue();
        assertThat(throttle.tryAcquire(Kind.REDIRECT, PHONE)).isTrue();
        assertThat(throttle.tryAcquire(Kind.NON_TEXT, "51922222222")).isTrue();
        assertThat(throttle.tryAcquire(Kind.REDIRECT, PHONE)).isFalse();
    }
}
