package pe.rmsolutions.chatbot.whatsapp.repository;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST Client de la Graph API de Meta (WhatsApp Cloud API). URL y timeouts en
 * {@code quarkus.rest-client.whatsapp-graph}.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@RegisterRestClient(configKey = "whatsapp-graph")
@RegisterProvider(GraphApiExceptionMapper.class)
public interface GraphApiClient {

    @POST
    @Path("/{apiVersion}/{phoneNumberId}/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void sendMessage(@PathParam("apiVersion") String apiVersion,
                     @PathParam("phoneNumberId") String phoneNumberId,
                     @HeaderParam("Authorization") String authorization,
                     GraphTextMessage message);
}
