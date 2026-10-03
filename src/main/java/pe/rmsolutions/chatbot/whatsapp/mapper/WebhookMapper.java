package pe.rmsolutions.chatbot.whatsapp.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.ReportingPolicy;
import pe.rmsolutions.chatbot.api.model.WhatsAppChange;
import pe.rmsolutions.chatbot.api.model.WhatsAppInboundMessage;
import pe.rmsolutions.chatbot.api.model.WhatsAppMessageEcho;
import pe.rmsolutions.chatbot.api.model.WhatsAppWebhookEvent;
import pe.rmsolutions.chatbot.whatsapp.model.InboundChange;
import pe.rmsolutions.chatbot.whatsapp.model.InboundEcho;
import pe.rmsolutions.chatbot.whatsapp.model.InboundMessage;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Convierte el payload del webhook de Meta (DTOs generados) a los records de dominio del canal.
 * Las listas ausentes se convierten en listas vacías.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@Mapper(componentModel = MappingConstants.ComponentModel.CDI, unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface WebhookMapper {

    default List<InboundChange> toChanges(WhatsAppWebhookEvent event) {
        if (event == null || event.getEntry() == null) {
            return List.of();
        }
        return event.getEntry().stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.getChanges() != null)
                .flatMap(entry -> entry.getChanges().stream())
                .filter(Objects::nonNull)
                .map(this::toChange)
                .toList();
    }

    @Mapping(target = "phoneNumberId", source = "value.metadata.phoneNumberId")
    @Mapping(target = "messages", source = "value.messages")
    @Mapping(target = "echoes", source = "value.messageEchoes")
    InboundChange toChange(WhatsAppChange change);

    @Mapping(target = "messageId", source = "id")
    @Mapping(target = "text", source = "text.body")
    InboundMessage toMessage(WhatsAppInboundMessage message);

    @Mapping(target = "messageId", source = "id")
    InboundEcho toEcho(WhatsAppMessageEcho echo);

    /**
     * Meta envía el timestamp como segundos epoch en texto. Un valor no numérico se descarta.
     */
    default Instant toInstant(String epochSeconds) {
        if (epochSeconds == null || !epochSeconds.matches("\\d{1,12}")) {
            return null;
        }
        return Instant.ofEpochSecond(Long.parseLong(epochSeconds));
    }
}
