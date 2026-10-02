package pe.rmsolutions.chatbot.sheets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class WorkbookProviderTest {

    private static final RawWorkbook EMPTY_RAW = new RawWorkbook(java.util.Map.of());

    private MutableClock clock;
    private AtomicInteger reads;
    private boolean failNextRead;
    private WorkbookProvider provider;

    @BeforeEach
    void preparar() {
        clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
        reads = new AtomicInteger();
        failNextRead = false;

        WorkbookSource source = () -> {
            reads.incrementAndGet();
            if (failNextRead) {
                throw new RuntimeException("Sheet no disponible");
            }
            return EMPTY_RAW;
        };

        SheetsConfig config = new SheetsConfig() {
            @Override
            public String spreadsheetId() {
                return "fake";
            }

            @Override
            public java.util.Optional<String> credentialsFile() {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<String> credentialsJson() {
                return java.util.Optional.empty();
            }

            @Override
            public Duration cacheTtl() {
                return Duration.ofSeconds(60);
            }

            @Override
            public Duration staleMax() {
                return Duration.ofMinutes(30);
            }
        };

        provider = new WorkbookProvider(source, new WorkbookParser(), config, clock);
    }

    @Test
    void noRecargaDentroDelTtl() {
        provider.get();
        clock.avanzar(Duration.ofSeconds(30));
        provider.get();

        assertThat(reads.get()).isEqualTo(1);
    }

    @Test
    void recargaAlVencerElTtl() {
        provider.get();
        clock.avanzar(Duration.ofSeconds(61));
        provider.get();

        assertThat(reads.get()).isEqualTo(2);
    }

    @Test
    void siFallaYLaUltimaCargaTieneMenosDeStaleMaxDevuelveLaAnterior() {
        ParsedWorkbook first = provider.get();

        clock.avanzar(Duration.ofMinutes(29));
        failNextRead = true;
        ParsedWorkbook second = provider.get();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void siFallaYNoHayCargaValidaLanzaExcepcion() {
        failNextRead = true;

        assertThatThrownBy(() -> provider.get())
                .isInstanceOf(WorkbookUnavailableException.class);
    }

    @Test
    void siFallaYLaUltimaCargaSuperaStaleMaxLanzaExcepcion() {
        provider.get();

        clock.avanzar(Duration.ofMinutes(31));
        failNextRead = true;

        assertThatThrownBy(() -> provider.get())
                .isInstanceOf(WorkbookUnavailableException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void avanzar(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
