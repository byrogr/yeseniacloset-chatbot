package pe.rmsolutions.chatbot.whatsapp.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import pe.rmsolutions.chatbot.api.WebhooksApi;
import pe.rmsolutions.chatbot.api.model.WhatsAppWebhookEvent;
import pe.rmsolutions.chatbot.whatsapp.mapper.WebhookMapper;
import pe.rmsolutions.chatbot.whatsapp.services.WebhookEventHandler;
import pe.rmsolutions.chatbot.whatsapp.services.WebhookVerifier;

/**
 * Webhook de WhatsApp Cloud API. Existe en producción. La firma se valida antes, en
 * {@link WebhookSignatureFilter}; aquí solo se delega y se responde de inmediato.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookResource implements WebhooksApi {

    private final WebhookMapper mapper;
    private final WebhookEventHandler handler;
    private final WebhookVerifier verifier;

    @Override
    public Response receiveWhatsAppEvents(WhatsAppWebhookEvent event) {
        handler.handle(mapper.toChanges(event));
        return Response.ok().build();
    }

    @Override
    public Response verifyWhatsAppWebhook(String hubMode, String hubVerifyToken, String hubChallenge) {
        return Response.ok(verifier.verify(hubMode, hubVerifyToken, hubChallenge), MediaType.TEXT_PLAIN).build();
    }

    /**
     * Un payload firmado que no se puede deserializar recibe 200 para que Meta no lo reintente. Solo se
     * registra el tipo de error: el mensaje de Jackson puede incluir fragmentos del cuerpo.
     */
    @ServerExceptionMapper
    public Response unreadablePayload(JsonProcessingException e) {
        log.error("Payload del webhook no interpretable ({}); se responde 200", e.getClass().getSimpleName());
        return Response.ok().build();
    }

    /**
     * Quarkus REST envuelve los errores de sintaxis JSON en un 400; para el webhook se tratan igual que
     * {@link #unreadablePayload}. Cualquier otro {@link WebApplicationException} conserva su respuesta.
     */
    @ServerExceptionMapper
    public Response wrappedUnreadablePayload(WebApplicationException e) {
        if (e.getCause() instanceof JsonProcessingException cause) {
            return unreadablePayload(cause);
        }
        return e.getResponse();
    }

    /**
     * Parámetros o cuerpo que no cumplen el contrato: en la verificación es un 403; en la recepción, un 200
     * por la misma razón que un payload no interpretable.
     */
    @ServerExceptionMapper
    public Response invalidRequest(ConstraintViolationException e, Request request) {
        if (HttpMethod.GET.equals(request.getMethod())) {
            return WhatsAppProblemMappers.forbidden();
        }
        log.error("Payload del webhook incompleto ({} violaciones); se responde 200", e.getConstraintViolations().size());
        return Response.ok().build();
    }
}
