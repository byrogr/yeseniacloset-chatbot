package pe.rmsolutions.chatbot.ops.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import pe.rmsolutions.chatbot.agent.model.ChatReply;
import pe.rmsolutions.chatbot.api.model.ConversationPause;
import pe.rmsolutions.chatbot.api.model.ConversationReply;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Convierte los resultados del agente a los DTOs del tag {@code Conversations}.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@Mapper(componentModel = MappingConstants.ComponentModel.CDI, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ConversationMapper {

    @Mapping(target = "reply", source = "text", qualifiedByName = "orNull")
    ConversationReply toDto(ChatReply source);

    @Mapping(target = "pausedUntil", source = "pausedUntil")
    ConversationPause toPause(Instant pausedUntil);

    @Named("orNull")
    default String orNull(Optional<String> text) {
        return text.orElse(null);
    }

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
