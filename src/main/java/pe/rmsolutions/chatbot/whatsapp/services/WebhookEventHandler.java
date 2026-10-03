package pe.rmsolutions.chatbot.whatsapp.services;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.rmsolutions.chatbot.agent.config.BotConfig;
import pe.rmsolutions.chatbot.agent.services.ConversationPauseRegistry;
import pe.rmsolutions.chatbot.agent.utils.MaskingUtils;
import pe.rmsolutions.chatbot.sheets.utils.PhoneNormalizer;
import pe.rmsolutions.chatbot.whatsapp.config.WhatsAppConfig;
import pe.rmsolutions.chatbot.whatsapp.model.InboundChange;
import pe.rmsolutions.chatbot.whatsapp.model.InboundEcho;
import pe.rmsolutions.chatbot.whatsapp.model.InboundMessage;

import java.util.List;

/**
 * Reparte un evento del webhook sin esperar el procesamiento: descarta otros números del negocio, deduplica
 * y encola los mensajes, y pausa el chat cuando llega un eco de la dueña. Los {@code statuses} se ignoran.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class WebhookEventHandler {

    static final String ECHOES_FIELD = "smb_message_echoes";

    private final WhatsAppConfig config;
    private final BotConfig botConfig;
    private final MessageDeduplicator deduplicator;
    private final InboundDispatcher dispatcher;
    private final ConversationPauseRegistry pauseRegistry;

    public void handle(List<InboundChange> changes) {
        for (InboundChange change : changes) {
            if (!config.phoneNumberId().equals(change.phoneNumberId())) {
                log.warn("Cambio descartado: phone_number_id distinto al configurado (field={})", change.field());
                continue;
            }
            change.messages().forEach(this::accept);
            if (ECHOES_FIELD.equals(change.field())) {
                change.echoes().forEach(this::pauseFor);
            }
        }
    }

    private void accept(InboundMessage message) {
        if (message.messageId() == null || message.from() == null) {
            log.warn("Mensaje descartado: sin id o sin remitente");
            return;
        }
        if (!deduplicator.firstTime(message.messageId())) {
            log.info("Mensaje {} duplicado; se ignora", message.messageId());
            return;
        }
        dispatcher.dispatch(message);
    }

    private void pauseFor(InboundEcho echo) {
        PhoneNormalizer.normalize(echo.to()).ifPresentOrElse(phone -> {
            pauseRegistry.pause(phone, botConfig.pause().duration());
            log.info("Eco {} de la dueña: chat {} pausado por {}", echo.messageId(), MaskingUtils.maskPhone(phone),
                    botConfig.pause().duration());
        }, () -> log.warn("Eco {} con destinatario no válido; se ignora", echo.messageId()));
    }
}
