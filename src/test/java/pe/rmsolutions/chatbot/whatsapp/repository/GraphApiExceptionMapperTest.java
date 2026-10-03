package pe.rmsolutions.chatbot.whatsapp.repository;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica la traducción de respuestas de error de la Graph API.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class GraphApiExceptionMapperTest {

    private final GraphApiExceptionMapper mapper = new GraphApiExceptionMapper();

    @Test
    void un5xxEsReintentable() {
        assertThat(mapper.toThrowable(response(502))).isInstanceOf(GraphApiServerException.class);
    }

    @Test
    void un4xxLlevaElCodigoDeErrorDeMeta() {
        Response response = response(400);
        when(response.readEntity(GraphErrorResponse.class)).thenReturn(
                new GraphErrorResponse(new GraphErrorResponse.GraphError(131030, null, "OAuthException", "X")));

        assertThat(mapper.toThrowable(response))
                .isInstanceOf(GraphApiClientException.class)
                .extracting("status", "errorCode").containsExactly(400, 131030);
    }

    @Test
    void un4xxSinCuerpoLegibleNoFalla() {
        Response response = response(401);
        when(response.readEntity(GraphErrorResponse.class)).thenThrow(new IllegalStateException("no json"));

        assertThat(mapper.toThrowable(response))
                .isInstanceOf(GraphApiClientException.class)
                .extracting("errorCode").isNull();
    }

    private static Response response(int status) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        return response;
    }
}
