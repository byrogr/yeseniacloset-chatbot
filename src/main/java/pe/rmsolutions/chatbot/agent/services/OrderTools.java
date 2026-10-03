package pe.rmsolutions.chatbot.agent.services;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.rmsolutions.chatbot.account.model.AccountStatus;
import pe.rmsolutions.chatbot.account.services.AccountStatusService;
import pe.rmsolutions.chatbot.agent.config.BotConfig;
import pe.rmsolutions.chatbot.agent.utils.MaskingUtils;
import pe.rmsolutions.chatbot.sheets.repository.WorkbookUnavailableException;

import java.math.BigDecimal;

/**
 * Tools del asistente. El celular llega siempre por {@link ToolMemoryId} (la memoria del turno), nunca
 * como parámetro que el modelo pueda elegir: así no puede consultar a otra clienta.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class OrderTools {

    static final String UNAVAILABLE = "{\"available\":false}";
    static final String HANDED_OFF = "{\"handedOff\":true}";

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .withConfigOverride(BigDecimal.class,
                    o -> o.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)))
            .build();

    private final AccountStatusService accountStatusService;
    private final TurnContext turnContext;
    private final ConversationPauseRegistry pauseRegistry;
    private final BotConfig config;

    @Tool("Returns the customer's orders, totals and payment dates. Call it when the customer ASKS about her "
            + "orders, garments, amounts, totals or payment dates. Do not call it when she reports a payment, asks "
            + "for more time to pay, or wants to order, add, cancel or change garments.")
    public String getAccountStatus(@ToolMemoryId String phone) {
        AccountStatus status;
        try {
            status = accountStatusService.getAccountStatus(phone);
        } catch (WorkbookUnavailableException e) {
            log.warn("Sheet no disponible al consultar la cuenta de {}", MaskingUtils.maskPhone(phone));
            return UNAVAILABLE;
        }
        turnContext.put(phone, status);
        try {
            return JSON.writeValueAsString(status);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el estado de cuenta", e);
        }
    }

    @Tool("Hands the conversation over to the store owner. Call it first, without calling getAccountStatus, when "
            + "the customer reports a payment, asks for more time to pay, wants to order, add, cancel or change "
            + "garments, or asks about anything other than her current orders, amounts and payment dates.")
    public String handOffToOwner(@ToolMemoryId String phone) {
        pauseRegistry.pause(phone, config.pause().duration());
        log.info("Conversación de {} derivada a la dueña", MaskingUtils.maskPhone(phone));
        return HANDED_OFF;
    }
}
