package pe.rmsolutions.chatbot.agent.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import pe.rmsolutions.chatbot.agent.repository.ExpiringChatMemoryStore;

/**
 * Memoria del asistente: ventana de {@code bot.memory.max-messages} mensajes por celular sobre
 * {@link ExpiringChatMemoryStore}.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public class ChatMemoryProducer {

    @Produces
    @ApplicationScoped
    public ChatMemoryProvider chatMemoryProvider(ExpiringChatMemoryStore store, BotConfig config) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(config.memory().maxMessages())
                .chatMemoryStore(store)
                .build();
    }
}
