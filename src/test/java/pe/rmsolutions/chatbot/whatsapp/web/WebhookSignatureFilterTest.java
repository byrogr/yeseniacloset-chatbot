package pe.rmsolutions.chatbot.whatsapp.web;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.SimpleResourceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.rmsolutions.chatbot.api.model.Problem;
import pe.rmsolutions.chatbot.whatsapp.WebhookSigner;
import pe.rmsolutions.chatbot.whatsapp.config.WhatsAppConfig;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica la validación HMAC del webhook sobre los bytes crudos del cuerpo.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class WebhookSignatureFilterTest {

    private static final String BODY = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}";

    private WebhookSignatureFilter filter;
    private SimpleResourceInfo resource;

    @BeforeEach
    void setUp() {
        WhatsAppConfig config = mock(WhatsAppConfig.class);
        when(config.appSecret()).thenReturn(WebhookSigner.TEST_APP_SECRET);
        filter = new WebhookSignatureFilter(config);
        resource = mock(SimpleResourceInfo.class);
        when(resource.getResourceClass()).thenAnswer(i -> WhatsAppWebhookResource.class);
        when(resource.getMethodName()).thenReturn("receiveWhatsAppEvents");
    }

    @Test
    void firmaValidaDejaPasarYRestauraElCuerpo() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        ContainerRequestContext request = request("POST", body, WebhookSigner.sign(body));

        assertThat(filter.verifySignature(request, resource)).isNull();

        assertThat(restoredBody(request)).isEqualTo(body);
    }

    @Test
    void firmaInvalidaDevuelve401ConProblem() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        ContainerRequestContext request = request("POST", body, WebhookSigner.sign("otro cuerpo"));

        Response response = filter.verifySignature(request, resource);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getMediaType()).hasToString("application/problem+json");
        assertThat(((Problem) response.getEntity()).getStatus()).isEqualTo(401);
        verify(request, never()).setEntityStream(any());
    }

    @Test
    void headerAusenteDevuelve401() throws Exception {
        ContainerRequestContext request = request("POST", BODY.getBytes(StandardCharsets.UTF_8), null);

        assertThat(filter.verifySignature(request, resource).getStatus()).isEqualTo(401);
    }

    @Test
    void headerSinPrefijoDevuelve401() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String hexOnly = WebhookSigner.sign(body).substring("sha256=".length());

        assertThat(filter.verifySignature(request("POST", body, hexOnly), resource).getStatus()).isEqualTo(401);
    }

    @Test
    void cuerpoConTildesYEmojisSeFirmaSobreLosBytesExactos() throws Exception {
        String text = "{\"text\":{\"body\":\"¿Cuánto debo? 👗💕 ñandú\"}}";
        byte[] body = text.getBytes(StandardCharsets.UTF_8);

        assertThat(filter.verifySignature(request("POST", body, WebhookSigner.sign(body)), resource)).isNull();

        // Firmar el texto escapado (como lo re-serializaría otro JSON) no debe coincidir.
        String escaped = "{\"text\":{\"body\":\"\\u00bfCu\\u00e1nto debo? \\ud83d\\udc57\\ud83d\\udc95 \\u00f1and\\u00fa\"}}";
        assertThat(filter.verifySignature(request("POST", body, WebhookSigner.sign(escaped)), resource).getStatus())
                .isEqualTo(401);
    }

    @Test
    void otrasOperacionesNoSeValidan() throws Exception {
        ContainerRequestContext get = request("GET", new byte[0], null);
        when(resource.getMethodName()).thenReturn("verifyWhatsAppWebhook");

        assertThat(filter.verifySignature(get, resource)).isNull();
        verify(get, never()).getEntityStream();
    }

    private static ContainerRequestContext request(String method, byte[] body, String signature) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getEntityStream()).thenReturn(new ByteArrayInputStream(body));
        when(request.getHeaderString("X-Hub-Signature-256")).thenReturn(signature);
        return request;
    }

    private static byte[] restoredBody(ContainerRequestContext request) throws Exception {
        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(request).setEntityStream(captor.capture());
        return captor.getValue().readAllBytes();
    }
}
