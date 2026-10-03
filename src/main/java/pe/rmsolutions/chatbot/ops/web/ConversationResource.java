package pe.rmsolutions.chatbot.ops.web;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import pe.rmsolutions.chatbot.agent.services.ChatService;
import pe.rmsolutions.chatbot.agent.services.ConversationNotPausedException;
import pe.rmsolutions.chatbot.agent.services.ConversationPauseRegistry;
import pe.rmsolutions.chatbot.api.ConversationsApi;
import pe.rmsolutions.chatbot.api.model.ConversationMessageRequest;
import pe.rmsolutions.chatbot.ops.mapper.ConversationMapper;

/**
 * Endpoints de desarrollo para simular conversaciones con el asistente sin WhatsApp y gestionar pausas.
 * Existen en los perfiles {@code dev} y {@code test}; no llegan al build de producción.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "test"})
@RequiredArgsConstructor
public class ConversationResource implements ConversationsApi {

    private final ChatService chatService;
    private final ConversationPauseRegistry pauseRegistry;
    private final ConversationMapper mapper;

    @Override
    public Response sendConversationMessage(String phone, ConversationMessageRequest request) {
        return Response.ok(mapper.toDto(chatService.reply(phone, request.getText()))).build();
    }

    @Override
    public Response getConversationPause(String phone) {
        return pauseRegistry.pausedUntil(phone)
                .map(until -> Response.ok(mapper.toPause(until)).build())
                .orElseThrow(ConversationNotPausedException::new);
    }

    @Override
    public Response deleteConversationPause(String phone) {
        pauseRegistry.resume(phone);
        return Response.noContent().build();
    }
}
