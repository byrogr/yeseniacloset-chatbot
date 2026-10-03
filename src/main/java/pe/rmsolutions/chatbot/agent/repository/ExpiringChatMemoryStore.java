package pe.rmsolutions.chatbot.agent.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.rmsolutions.chatbot.agent.config.BotConfig;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Memoria de conversación por celular, en memoria, que expira tras {@code bot.memory.ttl} sin actividad.
 * El reloj de expiración sale del {@link Clock} inyectado para poder probarlo.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
public class ExpiringChatMemoryStore implements ChatMemoryStore {

    private final Cache<Object, List<ChatMessage>> cache;

    @Inject
    public ExpiringChatMemoryStore(BotConfig config, Clock clock) {
        this(config.memory().ttl(), clock);
    }

    public ExpiringChatMemoryStore(Duration ttl, Clock clock) {
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(ttl)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMessage> messages = cache.getIfPresent(memoryId);
        return messages == null ? List.of() : messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        cache.put(memoryId, List.copyOf(messages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        cache.invalidate(memoryId);
    }
}
