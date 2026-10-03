package pe.rmsolutions.chatbot.whatsapp.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.rmsolutions.chatbot.whatsapp.config.WhatsAppConfig;

import java.time.Duration;

/**
 * Recuerda los ids de mensaje ya recibidos durante {@code whatsapp.dedupe-ttl}, porque Meta reintenta
 * la entrega si no recibe el 200 a tiempo. Vive solo en memoria.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
public class MessageDeduplicator {

    private final Cache<String, Boolean> seen;

    @Inject
    public MessageDeduplicator(WhatsAppConfig config) {
        this(config.dedupeTtl(), Ticker.systemTicker());
    }

    MessageDeduplicator(Duration ttl, Ticker ticker) {
        this.seen = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .ticker(ticker)
                .build();
    }

    /**
     * Devuelve {@code true} solo la primera vez que se ve el id dentro del TTL. Es atómico.
     */
    public boolean firstTime(String messageId) {
        return seen.asMap().putIfAbsent(messageId, Boolean.TRUE) == null;
    }
}
