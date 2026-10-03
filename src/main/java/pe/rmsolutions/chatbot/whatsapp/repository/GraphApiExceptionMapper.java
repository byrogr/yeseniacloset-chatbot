package pe.rmsolutions.chatbot.whatsapp.repository;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/**
 * Traduce las respuestas de error de la Graph API a excepciones que distinguen lo reintentable (5xx)
 * de lo definitivo (4xx).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public class GraphApiExceptionMapper implements ResponseExceptionMapper<RuntimeException> {

    @Override
    public RuntimeException toThrowable(Response response) {
        int status = response.getStatus();
        if (status >= 500) {
            return new GraphApiServerException(status);
        }
        return new GraphApiClientException(status, errorCode(response));
    }

    private static Integer errorCode(Response response) {
        try {
            GraphErrorResponse body = response.readEntity(GraphErrorResponse.class);
            return body == null || body.error() == null ? null : body.error().code();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
