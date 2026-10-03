package pe.rmsolutions.chatbot.whatsapp.services;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.rmsolutions.chatbot.agent.config.BotConfig;
import pe.rmsolutions.chatbot.agent.model.ChatReply;
import pe.rmsolutions.chatbot.agent.services.ChatService;
import pe.rmsolutions.chatbot.agent.services.ConversationPauseRegistry;
import pe.rmsolutions.chatbot.agent.services.HandoffMessages;
import pe.rmsolutions.chatbot.agent.utils.MaskingUtils;
import pe.rmsolutions.chatbot.observability.services.BotMetrics;
import pe.rmsolutions.chatbot.observability.services.BotMetrics.IgnoreReason;
import pe.rmsolutions.chatbot.sheets.utils.PhoneNormalizer;
import pe.rmsolutions.chatbot.whatsapp.config.WhatsAppConfig;
import pe.rmsolutions.chatbot.whatsapp.model.InboundMessage;
import pe.rmsolutions.chatbot.whatsapp.repository.OutboundMessenger;
import pe.rmsolutions.chatbot.whatsapp.services.NoticeThrottle.Kind;

import java.util.List;
import java.util.Optional;

/**
 * Procesa un mensaje entrante, en este orden: conversación pausada (silencio), bot apagado o celular fuera de
 * la lista de piloto (aviso de redirección), mensaje que no es texto (aviso) y, por último, el asistente.
 * Los avisos son textos fijos, sin LLM, limitados por {@link NoticeThrottle}. Nunca registra el texto de los
 * mensajes ni el celular completo.
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
    private final BotConfig botConfig;
    private final ConversationPauseRegistry pauseRegistry;
    private final HandoffMessages messages;
    private final NoticeThrottle noticeThrottle;
    private final BotMetrics metrics;

    public void process(InboundMessage message) {
        long start = System.nanoTime();
        String masked = MaskingUtils.maskPhone(message.from());
        Optional<String> normalized = PhoneNormalizer.normalize(message.from());
        if (normalized.isEmpty()) {
            log.info("Mensaje {} de {} sin respuesta: celular inválido", message.messageId(), masked);
            return;
        }
        String phone = normalized.get();
        if (pauseRegistry.isPaused(phone)) {
            log.info("Mensaje {} de {} sin respuesta: conversación pausada", message.messageId(), masked);
            return;
        }

        Optional<IgnoreReason> skipReason = skipReason(phone);
        if (skipReason.isPresent()) {
            log.info("Mensaje {} de {} no pasa al asistente: reason={}", message.messageId(), masked, skipReason.get());
            metrics.messageIgnored(skipReason.get());
            sendNotice(message, phone, Kind.REDIRECT, messages.redirectNotice());
            return;
        }

        if (!TEXT_TYPE.equals(message.type()) || message.text() == null || message.text().isBlank()) {
            log.info("Mensaje {} de {} no es texto: type={}", message.messageId(), masked, message.type());
            metrics.nonTextMessage();
            sendNotice(message, phone, Kind.NON_TEXT, Optional.of(messages.nonTextNotice()));
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

    private Optional<IgnoreReason> skipReason(String phone) {
        if (!botConfig.enabled()) {
            return Optional.of(IgnoreReason.DISABLED);
        }
        List<String> allowlist = botConfig.allowlist().orElse(List.of());
        if (!allowlist.isEmpty() && !allowlist.contains(phone)) {
            return Optional.of(IgnoreReason.NOT_ALLOWLISTED);
        }
        return Optional.empty();
    }

    // Sin texto (coexistencia) no hay aviso de redirección: la dueña ve el mensaje en su app.
    private void sendNotice(InboundMessage message, String phone, Kind kind, Optional<String> text) {
        if (text.isEmpty()) {
            return;
        }
        String masked = MaskingUtils.maskPhone(phone);
        if (!noticeThrottle.tryAcquire(kind, phone)) {
            log.info("Aviso {} a {} omitido: ya se envió en las últimas 12 h", kind, masked);
            metrics.messageIgnored(IgnoreReason.NOTICE_SUPPRESSED);
            return;
        }
        log.info("Aviso {} a {} send={}", kind, masked, send(message.from(), text.get()));
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
