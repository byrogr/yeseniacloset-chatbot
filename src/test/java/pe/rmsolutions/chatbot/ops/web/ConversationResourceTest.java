package pe.rmsolutions.chatbot.ops.web;

import dev.langchain4j.service.Result;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.agent.repository.ExpiringChatMemoryStore;
import pe.rmsolutions.chatbot.agent.services.ConversationPauseRegistry;
import pe.rmsolutions.chatbot.agent.services.OrderAssistant;

import java.time.Duration;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Prueba por HTTP las operaciones del tag {@code Conversations} con el modelo simulado.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@QuarkusTest
class ConversationResourceTest {

    private static final String PHONE = "51911111111";
    private static final String MESSAGES = "/api/v1/conversations/{phone}/messages";
    private static final String PAUSE = "/api/v1/conversations/{phone}/pause";

    @InjectMock
    OrderAssistant assistant;

    @Inject
    ConversationPauseRegistry pauses;
    @Inject
    ExpiringChatMemoryStore memoryStore;

    @BeforeEach
    void reset() {
        pauses.resume(PHONE);
        memoryStore.deleteMessages(PHONE);
    }

    @Test
    void enviarMensajeDevuelveConversationReply() {
        when(assistant.chat(anyString(), anyString(), anyString()))
                .thenReturn(Result.<String>builder().content("¡Hola Gaby!").toolExecutions(List.of()).build());

        given().pathParam("phone", PHONE).contentType(ContentType.JSON).body("{\"text\":\"hola\"}")
                .when().post(MESSAGES)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("reply", equalTo("Hola, soy el asistente automático de Yesenia.\n¡Hola Gaby!"))
                .body("toolsUsed", empty())
                .body("guardTriggered", equalTo(false))
                .body("paused", equalTo(false));
    }

    @Test
    void conversacionPausadaDevuelveReplyNulo() {
        pauses.pause(PHONE, Duration.ofHours(1));

        given().pathParam("phone", PHONE).contentType(ContentType.JSON).body("{\"text\":\"hola\"}")
                .when().post(MESSAGES)
                .then()
                .statusCode(200)
                .body("reply", nullValue())
                .body("paused", equalTo(true));
    }

    @Test
    void textoVacioDevuelve400ConProblem() {
        given().pathParam("phone", PHONE).contentType(ContentType.JSON).body("{\"text\":\"\"}")
                .when().post(MESSAGES)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("detail", containsString("text"));
    }

    @Test
    void celularInvalidoDevuelve400ConProblem() {
        given().pathParam("phone", "12345").contentType(ContentType.JSON).body("{\"text\":\"hola\"}")
                .when().post(MESSAGES)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("detail", not(containsString("12345")));
    }

    @Test
    void consultarPausaDevuelve404SinPausaY200ConPausa() {
        given().pathParam("phone", PHONE)
                .when().get(PAUSE)
                .then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("title", equalTo("Conversation not paused"))
                .body("detail", not(containsString(PHONE)));

        pauses.pause(PHONE, Duration.ofHours(12));

        given().pathParam("phone", PHONE)
                .when().get(PAUSE)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("pausedUntil", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z"));
    }

    @Test
    void borrarPausaEsIdempotente() {
        pauses.pause(PHONE, Duration.ofHours(12));

        given().pathParam("phone", PHONE).when().delete(PAUSE).then().statusCode(204);
        given().pathParam("phone", PHONE).when().delete(PAUSE).then().statusCode(204);

        given().pathParam("phone", PHONE).when().get(PAUSE).then().statusCode(404);
    }
}
