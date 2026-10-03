package pe.rmsolutions.chatbot.whatsapp.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import pe.rmsolutions.chatbot.agent.utils.MaskingUtils;
import pe.rmsolutions.chatbot.whatsapp.config.WhatsAppExecutorProducer;
import pe.rmsolutions.chatbot.whatsapp.model.InboundMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Encola los mensajes entrantes: los de un mismo celular se procesan en orden y de a uno (la memoria y el
 * contexto del turno lo asumen); los de celulares distintos, en paralelo. Un fallo no corta la cadena.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@Slf4j
public class InboundDispatcher {

    private final InboundMessageProcessor processor;
    private final Executor executor;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> chains = new ConcurrentHashMap<>();

    @Inject
    public InboundDispatcher(InboundMessageProcessor processor,
                             @Named(WhatsAppExecutorProducer.INBOUND_EXECUTOR) ExecutorService executor) {
        this((Executor) executor, processor);
    }

    InboundDispatcher(Executor executor, InboundMessageProcessor processor) {
        this.processor = processor;
        this.executor = executor;
    }

    /**
     * Encadena el mensaje detrás del último pendiente del mismo celular y devuelve sin esperar.
     *
     * @return futuro que se completa cuando el mensaje terminó de procesarse (nunca falla)
     */
    public CompletableFuture<Void> dispatch(InboundMessage message) {
        String phone = message.from();
        CompletableFuture<Void> next = chains.compute(phone, (key, previous) ->
                (previous == null ? CompletableFuture.<Void>completedFuture(null) : previous)
                        .thenRunAsync(() -> safeProcess(message), executor));
        next.whenComplete((ignored, error) -> chains.remove(phone, next));
        return next;
    }

    int activeChains() {
        return chains.size();
    }

    private void safeProcess(InboundMessage message) {
        try {
            processor.process(message);
        } catch (RuntimeException e) {
            log.error("Fallo al procesar el mensaje {} de {}", message.messageId(),
                    MaskingUtils.maskPhone(message.from()), e);
        }
    }
}
