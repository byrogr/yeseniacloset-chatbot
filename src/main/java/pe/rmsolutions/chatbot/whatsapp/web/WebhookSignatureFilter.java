package pe.rmsolutions.chatbot.whatsapp.web;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.SimpleResourceInfo;
import pe.rmsolutions.chatbot.observability.services.BotMetrics;
import pe.rmsolutions.chatbot.whatsapp.config.WhatsAppConfig;
import pe.rmsolutions.chatbot.whatsapp.utils.HmacUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Valida la firma {@code X-Hub-Signature-256} del {@code POST} del webhook sobre los bytes crudos del cuerpo,
 * antes de deserializar. Si la firma es válida, restaura el cuerpo para que el recurso lo lea normalmente.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@RequiredArgsConstructor
@Slf4j
public class WebhookSignatureFilter {

    static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    static final String RECEIVE_METHOD = "receiveWhatsAppEvents";

    private final WhatsAppConfig config;
    private final BotMetrics metrics;

    @ServerRequestFilter
    public Response verifySignature(ContainerRequestContext request, SimpleResourceInfo resource) throws IOException {
        if (!appliesTo(request, resource)) {
            return null;
        }
        byte[] body;
        try (InputStream in = request.getEntityStream()) {
            body = in.readAllBytes();
        }
        if (!HmacUtils.isValidSignature(config.appSecret(), body, request.getHeaderString(SIGNATURE_HEADER))) {
            log.warn("Webhook rechazado: firma {} ({} bytes)",
                    request.getHeaderString(SIGNATURE_HEADER) == null ? "ausente" : "inválida", body.length);
            metrics.invalidSignature();
            return WhatsAppProblemMappers.problem(Response.Status.UNAUTHORIZED, "Unauthorized",
                    "Missing or invalid webhook signature.");
        }
        request.setEntityStream(new ByteArrayInputStream(body));
        return null;
    }

    private static boolean appliesTo(ContainerRequestContext request, SimpleResourceInfo resource) {
        return HttpMethod.POST.equals(request.getMethod())
                && resource != null
                && WhatsAppWebhookResource.class.equals(resource.getResourceClass())
                && RECEIVE_METHOD.equals(resource.getMethodName());
    }
}
