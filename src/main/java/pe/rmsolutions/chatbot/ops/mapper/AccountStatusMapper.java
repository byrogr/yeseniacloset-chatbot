package pe.rmsolutions.chatbot.ops.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.ValueMapping;
import pe.rmsolutions.chatbot.api.model.AccountStatus;
import pe.rmsolutions.chatbot.api.model.CampaignAccount;
import pe.rmsolutions.chatbot.api.model.Catalog;
import pe.rmsolutions.chatbot.api.model.Garment;
import pe.rmsolutions.chatbot.api.model.GarmentReference;
import pe.rmsolutions.chatbot.api.model.GarmentStatus;
import pe.rmsolutions.chatbot.api.model.RecipientGroup;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Convierte el {@link pe.rmsolutions.chatbot.account.model.AccountStatus} de dominio al DTO
 * generado a partir del contrato OpenAPI.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@Mapper(componentModel = MappingConstants.ComponentModel.CDI, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountStatusMapper {

    @Mapping(target = "grandTotal", qualifiedByName = "money")
    AccountStatus toDto(pe.rmsolutions.chatbot.account.model.AccountStatus source);

    @Mapping(target = "campaignId", source = "campaignTitle")
    @Mapping(target = "catalog", qualifiedByName = "catalog")
    @Mapping(target = "paymentDueDate", source = "paymentDate")
    @Mapping(target = "soldOutItems", source = "soldOut")
    @Mapping(target = "unpricedItems", source = "unpriced")
    @Mapping(target = "total", qualifiedByName = "money")
    CampaignAccount toDto(pe.rmsolutions.chatbot.account.model.CampaignAccount source);

    @Mapping(target = "subtotal", qualifiedByName = "money")
    RecipientGroup toDto(pe.rmsolutions.chatbot.account.model.RecipientGroup source);

    @Mapping(target = "amount", qualifiedByName = "money")
    Garment toDto(pe.rmsolutions.chatbot.account.model.Garment source);

    GarmentReference toDto(pe.rmsolutions.chatbot.account.model.GarmentInfo source);

    @ValueMapping(source = "PENDIENTE", target = "PENDING")
    @ValueMapping(source = "ENTREGADO", target = "DELIVERED")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    GarmentStatus toDto(pe.rmsolutions.chatbot.sheets.model.OrderStatus source);

    @Named("money")
    default String money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @Named("catalog")
    default Catalog catalog(String catalog) {
        return catalog == null ? null : Catalog.fromValue(catalog.toUpperCase(Locale.ROOT));
    }
}
