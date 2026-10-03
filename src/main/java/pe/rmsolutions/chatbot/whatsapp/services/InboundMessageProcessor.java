package pe.rmsolutions.chatbot.whatsapp.services;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.rmsolutions.chatbot.agent.model.ChatReply;
import pe.rmsolutions.chatbot.agent.services.ChatService;
import pe.rmsolutions.chatbot.agent.utils.MaskingUtils;
import pe.rmsolutions.chatbot.whatsapp.config.WhatsAppConfig;
import pe.rmsolutions.chatbot.whatsapp.model.InboundMessage;
import pe.rmsolutions.chatbot.whatsapp.repository.OutboundMessenger;

/**
 * Procesa un mensaje entrante: ignora lo que no es texto, obtiene la respuesta del asistente y la envía.
 * Nunca registra el texto de los mensajes ni el celular completo.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class InboundMessageProcessor {

    static final String TEXT_TYPE = "text";

    private final ChatService chatService;
    private final OutboundMessenger messenger;
    private final WhatsAppConfig config;

    public void process(InboundMessage message) {
        long start = System.nanoTime();
        String masked = MaskingUtils.maskPhone(message.from());
        if (!TEXT_TYPE.equals(message.type()) || message.text() == null || message.text().isBlank()) {
            log.info("Mensaje {} de {} sin respuesta automática: type={}", message.messageId(), masked, message.type());
            return;
        }

        ChatReply reply = chatService.reply(message.from(), message.text());
        if (reply.text().isEmpty()) {
            log.info("Mensaje {} de {} sin respuesta: paused={} latencyMs={}",
                    message.messageId(), masked, reply.paused(), elapsedMs(start));
            return;
        }

        String body = truncate(reply.text().get(), config.maxTextLength());
        String result = send(message.from(), body);
        log.info("Mensaje {} de {} tools={} guardTriggered={} paused={} truncated={} latencyMs={} send={}",
                message.messageId(), masked, reply.toolsUsed(), reply.guardTriggered(), reply.paused(),
                body.length() < reply.text().get().length(), elapsedMs(start), result);
    }

    private String send(String to, String body) {
        try {
            messenger.sendText(to, body);
            return "OK";
        } catch (RuntimeException e) {
            log.error("Fallo al enviar la respuesta a {}", MaskingUtils.maskPhone(to), e);
            return "FAILED(" + e.getClass().getSimpleName() + ")";
        }
    }

    /**
     * Recorta al máximo de caracteres sin partir un par sustituto (emoji) por la mitad.
     */
    static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return text.substring(0, end);
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
