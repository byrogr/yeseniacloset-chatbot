package pe.rmsolutions.chatbot.ops.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import pe.rmsolutions.chatbot.ops.api.model.AccountStatus;
import pe.rmsolutions.chatbot.ops.api.model.CampaignAccount;
import pe.rmsolutions.chatbot.ops.api.model.Garment;
import pe.rmsolutions.chatbot.ops.api.model.GarmentInfo;
import pe.rmsolutions.chatbot.ops.api.model.RecipientGroup;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Convierte el {@link pe.rmsolutions.chatbot.account.model.AccountStatus} de dominio al DTO
 * generado a partir del contrato OpenAPI.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountStatusMapper {

    DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    AccountStatus toDto(pe.rmsolutions.chatbot.account.model.AccountStatus source);

    @Mapping(target = "paymentDate", expression = "java(format(source.paymentDate()))")
    CampaignAccount toDto(pe.rmsolutions.chatbot.account.model.CampaignAccount source);

    RecipientGroup toDto(pe.rmsolutions.chatbot.account.model.RecipientGroup source);

    Garment toDto(pe.rmsolutions.chatbot.account.model.Garment source);

    GarmentInfo toDto(pe.rmsolutions.chatbot.account.model.GarmentInfo source);

    @ValueMappings({
            @ValueMapping(source = "PAGADO", target = MappingConstants.THROW_EXCEPTION),
            @ValueMapping(source = "AGOTADO", target = MappingConstants.THROW_EXCEPTION),
            @ValueMapping(source = "CANCELADO", target = MappingConstants.THROW_EXCEPTION)
    })
    Garment.StatusEnum toDto(pe.rmsolutions.chatbot.sheets.model.OrderStatus source);

    default String format(LocalDate date) {
        return date == null ? null : DATE.format(date);
    }
}
