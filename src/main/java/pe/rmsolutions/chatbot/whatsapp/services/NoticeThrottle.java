package pe.rmsolutions.chatbot.whatsapp.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Limita los avisos fijos (mensaje que no es texto, redirección) a uno por celular y tipo cada 12 h, para no
 * responder a cada audio de una ráfaga. Vive solo en memoria.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
public class NoticeThrottle {

    static final Duration WINDOW = Duration.ofHours(12);

    /**
     * Tipos de aviso; cada uno lleva su propia ventana.
     *
     * @author Roger Rojas - roger.rojas@rmsolutions.pe
     */
    public enum Kind {
        NON_TEXT,
        REDIRECT
    }

    private final Cache<String, Boolean> sent;

    @Inject
    public NoticeThrottle(Clock clock) {
        this.sent = Caffeine.newBuilder()
                .expireAfterWrite(WINDOW)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    /**
     * Devuelve {@code true} si el aviso puede enviarse ahora y registra el envío. Es atómico.
     */
    public boolean tryAcquire(Kind kind, String phone) {
        return sent.asMap().putIfAbsent(kind + ":" + phone, Boolean.TRUE) == null;
    }
}
