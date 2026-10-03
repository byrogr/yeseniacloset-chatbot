package pe.rmsolutions.chatbot.whatsapp.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.api.model.WhatsAppWebhookEvent;
import pe.rmsolutions.chatbot.whatsapp.WebhookFixtures;
import pe.rmsolutions.chatbot.whatsapp.model.InboundChange;
import pe.rmsolutions.chatbot.whatsapp.model.InboundEcho;
import pe.rmsolutions.chatbot.whatsapp.model.InboundMessage;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la conversión de los 6 payloads de ejemplo del webhook a records de dominio.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class WebhookMapperTest {

    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final WebhookMapper mapper = new WebhookMapperImpl();

    @Test
    void mensajeDeTexto() {
        InboundChange change = single("text-message.json");

        assertThat(change.field()).isEqualTo("messages");
        assertThat(change.phoneNumberId()).isEqualTo("TEST_PHONE_NUMBER_ID");
        assertThat(change.echoes()).isEmpty();
        assertThat(change.messages()).containsExactly(new InboundMessage("wamid.TEST001", "51911111111", "text",
                "hola cuanto era lo mio?", Instant.ofEpochSecond(1790900000L)));
    }

    @Test
    void imagenSinTexto() {
        InboundMessage message = single("image-message.json").messages().getFirst();

        assertThat(message.type()).isEqualTo("image");
        assertThat(message.text()).isNull();
        assertThat(message.messageId()).isEqualTo("wamid.TEST002");
    }

    @Test
    void estadoDeEntregaNoTraeMensajesNiEcos() {
        InboundChange change = single("status-update.json");

        assertThat(change.messages()).isEmpty();
        assertThat(change.echoes()).isEmpty();
    }

    @Test
    void ecoDeLaDuena() {
        InboundChange change = single("echo-message.json");

        assertThat(change.field()).isEqualTo("smb_message_echoes");
        assertThat(change.messages()).isEmpty();
        assertThat(change.echoes()).containsExactly(new InboundEcho("wamid.ECHO001", "51911111111"));
    }

    @Test
    void dosMensajesEnOrden() {
        InboundChange change = single("two-messages.json");

        assertThat(change.messages()).extracting(InboundMessage::messageId, InboundMessage::text)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("wamid.TEST003", "hola"),
                        org.assertj.core.groups.Tuple.tuple("wamid.TEST004", "cuando pago?"));
    }

    @Test
    void otroPhoneNumberId() {
        InboundChange change = single("other-phone-number-id.json");

        assertThat(change.phoneNumberId()).isEqualTo("OTHER_PHONE_NUMBER_ID");
        assertThat(change.messages()).hasSize(1);
    }

    @Test
    void eventoSinEntradasDevuelveListaVacia() {
        assertThat(mapper.toChanges(new WhatsAppWebhookEvent())).isEmpty();
        assertThat(mapper.toChanges(null)).isEmpty();
    }

    @Test
    void timestampNoNumericoSeDescarta() {
        assertThat(mapper.toInstant("abc")).isNull();
        assertThat(mapper.toInstant(null)).isNull();
    }

    private InboundChange single(String fixture) {
        try {
            List<InboundChange> changes = mapper.toChanges(
                    json.readValue(WebhookFixtures.read(fixture), WhatsAppWebhookEvent.class));
            assertThat(changes).hasSize(1);
            return changes.getFirst();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
