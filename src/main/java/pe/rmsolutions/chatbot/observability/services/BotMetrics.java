package pe.rmsolutions.chatbot.observability.services;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Locale;

/**
 * Métricas de negocio del bot, exportadas a Application Insights por OpenTelemetry.
 *
 * <p><b>Ningún atributo lleva celulares, nombres ni texto</b>: los valores salen de enums o del código de error
 * numérico de Meta. Los motivos de derivación tampoco se registran, porque los redacta el LLM.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
public class BotMetrics {

    static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");
    static final AttributeKey<String> REASON = AttributeKey.stringKey("reason");
    static final AttributeKey<String> META_ERROR_CODE = AttributeKey.stringKey("metaErrorCode");

    /**
     * Resultado de un turno del asistente.
     *
     * @author Roger Rojas - roger.rojas@rmsolutions.pe
     */
    public enum TurnOutcome {
        REPLIED,
        PAUSED,
        ERROR
    }

    /**
     * Motivo por el que un mensaje no llegó al asistente o no recibió aviso.
     *
     * @author Roger Rojas - roger.rojas@rmsolutions.pe
     */
    public enum IgnoreReason {
        DISABLED,
        NOT_ALLOWLISTED,
        NOTICE_SUPPRESSED
    }

    private final LongCounter turns;
    private final DoubleHistogram turnDuration;
    private final LongCounter guardTriggered;
    private final LongCounter handoffs;
    private final LongCounter messagesIgnored;
    private final LongCounter nonTextMessages;
    private final LongCounter outboundErrors;
    private final LongCounter invalidSignatures;

    @Inject
    public BotMetrics(Meter meter) {
        this.turns = meter.counterBuilder("chatbot.turns")
                .setDescription("Turnos del asistente por resultado").build();
        this.turnDuration = meter.histogramBuilder("chatbot.turn.duration")
                .setDescription("Duración de un turno del asistente").setUnit("ms").build();
        this.guardTriggered = meter.counterBuilder("chatbot.guard.triggered")
                .setDescription("Respuestas del modelo rechazadas por la guardia de montos").build();
        this.handoffs = meter.counterBuilder("chatbot.handoffs")
                .setDescription("Conversaciones derivadas a la dueña").build();
        this.messagesIgnored = meter.counterBuilder("chatbot.messages.ignored")
                .setDescription("Mensajes que no pasaron al asistente o cuyo aviso se omitió").build();
        this.nonTextMessages = meter.counterBuilder("chatbot.messages.non_text")
                .setDescription("Mensajes que no son texto (audio, imagen, etc.)").build();
        this.outboundErrors = meter.counterBuilder("chatbot.outbound.errors")
                .setDescription("Intentos de envío fallidos a la Cloud API").build();
        this.invalidSignatures = meter.counterBuilder("chatbot.webhook.signature_invalid")
                .setDescription("Webhooks rechazados por firma ausente o inválida").build();
    }

    public void turn(TurnOutcome outcome) {
        turns.add(1, Attributes.of(OUTCOME, lower(outcome)));
    }

    public void turnDuration(long millis) {
        turnDuration.record(millis);
    }

    public void guardTriggered() {
        guardTriggered.add(1);
    }

    public void handoff() {
        handoffs.add(1);
    }

    public void messageIgnored(IgnoreReason reason) {
        messagesIgnored.add(1, Attributes.of(REASON, lower(reason)));
    }

    public void nonTextMessage() {
        nonTextMessages.add(1);
    }

    /**
     * @param metaErrorCode {@code error.code} de Meta; {@code null} si no hubo respuesta de Meta (5xx o timeout)
     */
    public void outboundError(Integer metaErrorCode) {
        outboundErrors.add(1, Attributes.of(META_ERROR_CODE, metaErrorCode == null ? "none" : metaErrorCode.toString()));
    }

    public void invalidSignature() {
        invalidSignatures.add(1);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
