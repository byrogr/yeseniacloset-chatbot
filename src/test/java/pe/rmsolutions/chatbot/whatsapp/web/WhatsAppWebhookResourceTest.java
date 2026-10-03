package pe.rmsolutions.chatbot.whatsapp.web;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import pe.rmsolutions.chatbot.agent.model.ChatReply;
import pe.rmsolutions.chatbot.agent.services.ChatService;
import pe.rmsolutions.chatbot.agent.services.ConversationPauseRegistry;
import pe.rmsolutions.chatbot.whatsapp.WebhookFixtures;
import pe.rmsolutions.chatbot.whatsapp.WebhookSigner;
import pe.rmsolutions.chatbot.whatsapp.repository.OutboundMessenger;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba por HTTP el webhook de WhatsApp con el asistente y el envío simulados.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@QuarkusTest
class WhatsAppWebhookResourceTest {

    private static final String PHONE = "51911111111";
    private static final String WEBHOOK = "/api/v1/webhooks/whatsapp";
    private static final Duration QUIET = Duration.ofMillis(500);

    @InjectMock
    ChatService chatService;
    @InjectMock
    OutboundMessenger messenger;

    @Inject
    ConversationPauseRegistry pauses;

    @BeforeEach
    void setUp() {
        pauses.resume(PHONE);
        when(chatService.reply(eq(PHONE), anyString()))
                .thenReturn(new ChatReply(Optional.of("Hola Gaby"), List.of(), false, false));
    }

    // ---------- verifyWhatsAppWebhook ----------

    @Test
    void verificacionConTokenCorrectoDevuelveElChallenge() {
        verification("subscribe", "test-verify-token", "1158201444")
                .statusCode(200)
                .contentType(ContentType.TEXT)
                .body(equalTo("1158201444"));
    }

    @Test
    void verificacionConTokenIncorrectoDevuelve403() {
        verification("subscribe", "otro-token", "1158201444")
                .statusCode(403)
                .contentType("application/problem+json")
                .body("status", equalTo(403));
    }

    @Test
    void verificacionConModoIncorrectoODatosFaltantesDevuelve403() {
        verification("unsubscribe", "test-verify-token", "1").statusCode(403);

        given().queryParam("hub.mode", "subscribe").queryParam("hub.challenge", "1")
                .when().get(WEBHOOK)
                .then().statusCode(403).contentType("application/problem+json");
    }

    // ---------- receiveWhatsAppEvents: firma ----------

    @Test
    void postSinFirmaDevuelve401() {
        given().contentType(ContentType.JSON).body(fixture("text-message.json"))
                .when().post(WEBHOOK)
                .then()
                .statusCode(401)
                .contentType("application/problem+json")
                .body("status", equalTo(401));

        assertNothingSent();
    }

    @Test
    void postConFirmaInvalidaDevuelve401() {
        byte[] body = fixture("text-message.json");
        given().contentType(ContentType.JSON).header("X-Hub-Signature-256", WebhookSigner.sign("otro"))
                .body(body)
                .when().post(WEBHOOK)
                .then().statusCode(401);

        assertNothingSent();
    }

    // ---------- receiveWhatsAppEvents: procesamiento ----------

    @Test
    void mensajeDeTextoSeRespondeUnaVez() {
        post(fixture("text-message.json")).statusCode(200);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(messenger).sendText(PHONE, "Hola Gaby"));
    }

    @Test
    void elMismoPayloadDosVecesSeRespondeUnaSolaVez() {
        byte[] body = fixture("text-message.json");
        post(body).statusCode(200);
        post(body).statusCode(200);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(messenger).sendText(eq(PHONE), anyString()));
        await().during(QUIET).atMost(QUIET.multipliedBy(3))
                .untilAsserted(() -> verify(messenger, times(1)).sendText(anyString(), anyString()));
    }

    @Test
    void ecoDeLaDuenaPausaElChat12HorasSinEnviarNada() {
        Instant before = Instant.now();

        post(fixture("echo-message.json")).statusCode(200);

        assertThat(pauses.isPaused(PHONE)).isTrue();
        assertThat(pauses.pausedUntil(PHONE)).get()
                .satisfies(until -> assertThat(until).isBetween(
                        before.plus(Duration.ofHours(12)), Instant.now().plus(Duration.ofHours(12))));
        assertNothingSent();
    }

    @Test
    void imagenRecibeElAvisoSinPasarPorElAsistente() {
        post(fixture("image-message.json")).statusCode(200);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(messenger)
                .sendText(eq(PHONE), contains("solo puedo leer mensajes de texto")));
        verify(chatService, never()).reply(anyString(), anyString());
    }

    @Test
    void estadoYOtroNumeroNoEnvianNada() {
        post(fixture("status-update.json")).statusCode(200);
        post(fixture("other-phone-number-id.json")).statusCode(200);

        assertNothingSent();
        verify(chatService, never()).reply(anyString(), anyString());
    }

    @Test
    void dosMensajesSeRespondenEnOrden() {
        when(chatService.reply(PHONE, "hola"))
                .thenReturn(new ChatReply(Optional.of("R1"), List.of(), false, false));
        when(chatService.reply(PHONE, "cuando pago?"))
                .thenReturn(new ChatReply(Optional.of("R2"), List.of(), false, false));

        post(fixture("two-messages.json")).statusCode(200);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(messenger, times(2)).sendText(eq(PHONE), anyString()));
        InOrder order = inOrder(messenger);
        order.verify(messenger).sendText(PHONE, "R1");
        order.verify(messenger).sendText(PHONE, "R2");
    }

    @Test
    void respondeEnMenosDe500msAunqueElAsistenteTarde3s() {
        when(chatService.reply(eq(PHONE), anyString())).thenAnswer(invocation -> {
            Thread.sleep(3000);
            return new ChatReply(Optional.of("Hola Gaby"), List.of(), false, false);
        });
        byte[] body = fixture("text-message.json");

        long start = System.nanoTime();
        post(body).statusCode(200);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(500);
        await().atMost(Duration.ofSeconds(6)).untilAsserted(() -> verify(messenger).sendText(PHONE, "Hola Gaby"));
    }

    @Test
    void payloadNoInterpretableDevuelve200() {
        post("{esto no es json".getBytes(StandardCharsets.UTF_8)).statusCode(200);
        post("{}".getBytes(StandardCharsets.UTF_8)).statusCode(200);
        post("{\"object\":\"whatsapp_business_account\",\"entry\":\"no-es-lista\"}"
                .getBytes(StandardCharsets.UTF_8)).statusCode(200);

        assertNothingSent();
    }

    private void assertNothingSent() {
        await().during(QUIET).atMost(QUIET.multipliedBy(3))
                .untilAsserted(() -> verify(messenger, never()).sendText(anyString(), anyString()));
    }

    private static ValidatableResponse verification(String mode, String token, String challenge) {
        return given()
                .queryParam("hub.mode", mode)
                .queryParam("hub.verify_token", token)
                .queryParam("hub.challenge", challenge)
                .when().get(WEBHOOK)
                .then();
    }

    private static ValidatableResponse post(byte[] body) {
        return given()
                .contentType(ContentType.JSON)
                .header("X-Hub-Signature-256", WebhookSigner.sign(body))
                .body(body)
                .when().post(WEBHOOK)
                .then();
    }

    /**
     * Cada test usa ids de mensaje propios para que la deduplicación de un test no afecte a otro.
     */
    private static byte[] fixture(String name) {
        String unique = "wamid." + UUID.randomUUID() + "-";
        return WebhookFixtures.read(name).replace("wamid.", unique).getBytes(StandardCharsets.UTF_8);
    }
}
