package pe.rmsolutions.chatbot.agent.services;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Conversación con el modelo. {@code phone} es el celular ya normalizado y solo se usa como id de
 * memoria: el modelo nunca lo recibe como parámetro.
 *
 * <p>Es {@code @ApplicationScoped} porque con el scope por defecto ({@code @RequestScoped}) la extensión
 * borra la memoria al terminar cada request.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@RegisterAiService(tools = OrderTools.class)
@ApplicationScoped
@SystemMessage(fromResource = "prompts/system-prompt.txt")
public interface OrderAssistant {

    Result<String> chat(@MemoryId String phone, @UserMessage String message, @V("ownerName") String ownerName);
}
