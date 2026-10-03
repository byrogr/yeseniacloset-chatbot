package pe.rmsolutions.chatbot.agent.services;

import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.service.Result;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.rmsolutions.chatbot.account.model.AccountStatus;
import pe.rmsolutions.chatbot.account.services.AccountStatusService;
import pe.rmsolutions.chatbot.agent.config.BotConfig;
import pe.rmsolutions.chatbot.agent.model.ChatReply;
import pe.rmsolutions.chatbot.agent.model.GuardResult;
import pe.rmsolutions.chatbot.agent.repository.ExpiringChatMemoryStore;
import pe.rmsolutions.chatbot.agent.utils.MaskingUtils;
import pe.rmsolutions.chatbot.sheets.repository.WorkbookUnavailableException;
import pe.rmsolutions.chatbot.sheets.utils.PhoneNormalizer;

import java.util.List;
import java.util.Optional;

/**
 * Orquesta un turno completo: pausa, modelo, guardia de montos y presentación. Es el punto de entrada
 * para {@code ops} y para el canal de WhatsApp. Nunca registra el texto de los mensajes.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final OrderAssistant assistant;
    private final ExpiringChatMemoryStore memoryStore;
    private final ConversationPauseRegistry pauseRegistry;
    private final TurnContext turnContext;
    private final AmountGuard amountGuard;
    private final AccountStatusFormatter formatter;
    private final AccountStatusService accountStatusService;
    private final BotConfig config;

    public ChatReply reply(String rawPhone, String message) {
        Optional<String> normalized = PhoneNormalizer.normalize(rawPhone);
        if (normalized.isEmpty()) {
            return new ChatReply(Optional.empty(), List.of(), false, false);
        }
        String phone = normalized.get();
        String masked = MaskingUtils.maskPhone(phone);
        if (pauseRegistry.isPaused(phone)) {
            log.info("Turno omitido para {}: conversación pausada", masked);
            return new ChatReply(Optional.empty(), List.of(), false, true);
        }

        long start = System.nanoTime();
        boolean firstMessage = memoryStore.getMessages(phone).isEmpty();
        List<String> toolsUsed = List.of();
        boolean guardTriggered = false;
        String text;

        turnContext.clear(phone);
        try {
            Result<String> result = assistant.chat(phone, message, config.ownerName());
            toolsUsed = result.toolExecutions().stream().map(t -> t.request().name()).toList();
            text = result.content();

            GuardResult guard = amountGuard.check(text, turnContext.get(phone).orElse(null));
            if (guard.status() == GuardResult.Status.VIOLATION) {
                guardTriggered = true;
                log.warn("Guardia de montos activada para {}: montos no permitidos {}", masked, guard.unknownAmounts());
                AccountStatus status = turnContext.get(phone)
                        .orElseGet(() -> accountStatusService.getAccountStatus(phone));
                text = formatter.format(status, config.ownerName());
            }
        } catch (WorkbookUnavailableException e) {
            log.error("Sheet no disponible al formatear el respaldo para {}", masked, e);
            text = fallbackAndPause(phone);
        } catch (RuntimeException e) {
            if (isContentFiltered(e)) {
                log.warn("Mensaje de {} bloqueado por el filtro de contenido de Azure; se deriva a la dueña", masked);
                pauseRegistry.pause(phone, config.pause().duration());
                text = config.ownerName() + " te escribirá pronto.";
            } else {
                log.error("Fallo del modelo para {}", masked, e);
                text = fallbackAndPause(phone);
            }
        } finally {
            turnContext.clear(phone);
        }

        if (firstMessage) {
            text = "Hola, soy el asistente automático de " + config.ownerName() + ".\n" + text;
        }
        boolean paused = pauseRegistry.isPaused(phone);
        log.info("Turno {} tools={} guardTriggered={} paused={} latencyMs={}",
                masked, toolsUsed, guardTriggered, paused, (System.nanoTime() - start) / 1_000_000);
        return new ChatReply(Optional.of(text), toolsUsed, guardTriggered, paused);
    }

    // La extensión de Azure no siempre traduce el 400 de Azure a ContentFilteredException.
    private static boolean isContentFiltered(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ContentFilteredException) {
                return true;
            }
            if (t instanceof HttpException http && http.statusCode() == 400
                    && String.valueOf(http.getMessage()).contains("content_filter")) {
                return true;
            }
        }
        return false;
    }

    private String fallbackAndPause(String phone) {
        pauseRegistry.pause(phone, config.pause().duration());
        return "En este momento no puedo revisar tu pedido. " + config.ownerName() + " te escribirá pronto.";
    }
}
