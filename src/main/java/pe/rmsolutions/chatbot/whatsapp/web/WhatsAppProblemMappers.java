package pe.rmsolutions.chatbot.whatsapp.web;

import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import pe.rmsolutions.chatbot.api.model.Problem;
import pe.rmsolutions.chatbot.whatsapp.services.WebhookVerificationException;

/**
 * Respuestas {@code application/problem+json} (RFC 9457) del canal de WhatsApp.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public class WhatsAppProblemMappers {

    static final String PROBLEM_JSON = "application/problem+json";

    @ServerExceptionMapper
    public Response verificationFailed(WebhookVerificationException e) {
        return forbidden();
    }

    static Response forbidden() {
        return problem(Response.Status.FORBIDDEN, "Forbidden", "Webhook verification failed.");
    }

    static Response problem(Response.Status status, String title, String detail) {
        Problem body = new Problem()
                .title(title)
                .status(status.getStatusCode())
                .detail(detail);
        return Response.status(status).type(PROBLEM_JSON).entity(body).build();
    }
}
