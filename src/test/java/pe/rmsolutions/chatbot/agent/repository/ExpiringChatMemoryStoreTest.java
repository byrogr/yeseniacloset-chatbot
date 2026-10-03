package pe.rmsolutions.chatbot.agent.repository;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.agent.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class ExpiringChatMemoryStoreTest {

    private static final String PHONE = "51911111111";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-02T10:00:00Z"));
    private final ExpiringChatMemoryStore store = new ExpiringChatMemoryStore(Duration.ofMinutes(30), clock);

    @Test
    void guardaYDevuelveLosMensajesPorCelular() {
        store.updateMessages(PHONE, List.of(UserMessage.from("hola"), AiMessage.from("Hola Gaby")));

        assertThat(store.getMessages(PHONE)).hasSize(2);
        assertThat(store.getMessages("51922222222")).isEmpty();
    }

    @Test
    void expiraTrasElTtlSinActividad() {
        store.updateMessages(PHONE, List.of(UserMessage.from("hola")));

        clock.advance(Duration.ofMinutes(29));
        assertThat(store.getMessages(PHONE)).hasSize(1);

        clock.advance(Duration.ofMinutes(29));
        assertThat(store.getMessages(PHONE)).as("el acceso anterior renovó el TTL").hasSize(1);

        clock.advance(Duration.ofMinutes(30));
        assertThat(store.getMessages(PHONE)).isEmpty();
    }

    @Test
    void deleteBorraLaConversacion() {
        store.updateMessages(PHONE, List.of(UserMessage.from("hola")));

        store.deleteMessages(PHONE);

        assertThat(store.getMessages(PHONE)).isEmpty();
    }
}
