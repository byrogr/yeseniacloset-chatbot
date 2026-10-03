package pe.rmsolutions.chatbot.whatsapp.repository;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifica el cuerpo enviado a la Graph API y la política de reintentos (en {@code test} el delay es de 10 ms).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@QuarkusTest
class CloudApiMessengerTest {

    private static final String PHONE = "51911111111";

    @InjectMock
    @RestClient
    GraphApiClient client;

    @Inject
    CloudApiMessenger messenger;

    @Test
    void enviaElCuerpoDeTextoConVersionPhoneNumberIdYToken() {
        messenger.sendText(PHONE, "Hola Gaby");

        ArgumentCaptor<GraphTextMessage> body = ArgumentCaptor.forClass(GraphTextMessage.class);
        verify(client).sendMessage(eq("v25.0"), eq("TEST_PHONE_NUMBER_ID"), eq("Bearer test-access-token"),
                body.capture());
        assertThat(body.getValue()).isEqualTo(new GraphTextMessage("whatsapp", "individual", PHONE, "text",
                new GraphTextMessage.Text(false, "Hola Gaby")));
    }

    @Test
    void un5xxSeReintentaUnaVez() {
        doThrow(new GraphApiServerException(503)).doNothing()
                .when(client).sendMessage(any(), any(), any(), any());

        messenger.sendText(PHONE, "hola");

        verify(client, times(2)).sendMessage(any(), any(), any(), any());
    }

    @Test
    void unTimeoutSeReintentaUnaVezYLuegoFalla() {
        doThrow(new ProcessingException("timeout")).when(client).sendMessage(any(), any(), any(), any());

        assertThatThrownBy(() -> messenger.sendText(PHONE, "hola")).isInstanceOf(ProcessingException.class);

        verify(client, times(2)).sendMessage(any(), any(), any(), any());
    }

    @Test
    void un4xxNoSeReintenta() {
        doThrow(new GraphApiClientException(400, 131030)).when(client).sendMessage(any(), any(), any(), any());

        assertThatThrownBy(() -> messenger.sendText(PHONE, "hola"))
                .isInstanceOf(GraphApiClientException.class)
                .extracting("errorCode").isEqualTo(131030);

        verify(client, times(1)).sendMessage(any(), any(), any(), any());
    }
}
