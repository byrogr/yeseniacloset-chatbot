package pe.rmsolutions.chatbot.ops.mapper;

import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.account.model.AccountStatus;
import pe.rmsolutions.chatbot.account.model.CampaignAccount;
import pe.rmsolutions.chatbot.account.model.Garment;
import pe.rmsolutions.chatbot.account.model.GarmentInfo;
import pe.rmsolutions.chatbot.account.model.RecipientGroup;
import pe.rmsolutions.chatbot.api.model.Catalog;
import pe.rmsolutions.chatbot.api.model.GarmentStatus;
import pe.rmsolutions.chatbot.sheets.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica la conversión de {@link AccountStatus} de dominio al DTO del contrato OpenAPI.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class AccountStatusApiMapperTest {

    private final AccountStatusMapper mapper = new AccountStatusMapperImpl();

    @Test
    void mapeaCamposMontosEnumsYFechas() {
        var garment = new Garment("Vestido lila", "XS", new BigDecimal("35.9"), OrderStatus.PENDIENTE);
        var delivered = new Garment("Blusa", null, new BigDecimal("10"), OrderStatus.ENTREGADO);
        var group = new RecipientGroup(null, List.of(garment, delivered), new BigDecimal("45.9"));
        var soldOut = new GarmentInfo("Vestido azul", "S", "Ana");
        var unpriced = new GarmentInfo("Falda", null, null);
        var campaign = new CampaignAccount("C-11-pacifika", 11, "pacifika", LocalDate.of(2026, 10, 15),
                List.of(group), List.of(soldOut), List.of(unpriced), new BigDecimal("45.90"));
        var status = new AccountStatus(true, "Gaby", List.of(campaign), new BigDecimal("1.005"));

        var dto = mapper.toDto(status);

        assertThat(dto.getCustomerName()).isEqualTo("Gaby");
        assertThat(dto.getGrandTotal()).isEqualTo("1.01");

        var c = dto.getCampaigns().getFirst();
        assertThat(c.getCampaignId()).isEqualTo("C-11-pacifika");
        assertThat(c.getNumber()).isEqualTo(11);
        assertThat(c.getCatalog()).isEqualTo(Catalog.PACIFIKA);
        assertThat(c.getPaymentDueDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(c.getTotal()).isEqualTo("45.90");

        var g = c.getGroups().getFirst();
        assertThat(g.getRecipient()).isNull();
        assertThat(g.getSubtotal()).isEqualTo("45.90");
        assertThat(g.getItems()).extracting("product", "size", "amount", "status")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Vestido lila", "XS", "35.90", GarmentStatus.PENDING),
                        org.assertj.core.groups.Tuple.tuple("Blusa", null, "10.00", GarmentStatus.DELIVERED));

        assertThat(c.getSoldOutItems()).singleElement().satisfies(r -> {
            assertThat(r.getProduct()).isEqualTo("Vestido azul");
            assertThat(r.getSize()).isEqualTo("S");
            assertThat(r.getRecipient()).isEqualTo("Ana");
        });
        assertThat(c.getUnpricedItems()).singleElement().satisfies(r -> {
            assertThat(r.getProduct()).isEqualTo("Falda");
            assertThat(r.getRecipient()).isNull();
        });
    }

    @Test
    void fechaDePagoNulaYCatalogoCarmel() {
        var campaign = new CampaignAccount("C-12-Carmel", 12, "carmel", null,
                List.of(), List.of(), List.of(), BigDecimal.ZERO);

        var dto = mapper.toDto(campaign);

        assertThat(dto.getPaymentDueDate()).isNull();
        assertThat(dto.getCatalog()).isEqualTo(Catalog.CARMEL);
        assertThat(dto.getTotal()).isEqualTo("0.00");
    }

    @Test
    void estadosNoCobrablesSonErrorDeProgramacion() {
        assertThatThrownBy(() -> mapper.toDto(OrderStatus.AGOTADO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.toDto(OrderStatus.PAGADO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.toDto(OrderStatus.CANCELADO)).isInstanceOf(IllegalArgumentException.class);
    }
}
