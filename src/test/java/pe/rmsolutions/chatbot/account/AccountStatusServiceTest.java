package pe.rmsolutions.chatbot.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.sheets.FixtureWorkbookSource;
import pe.rmsolutions.chatbot.sheets.SheetsConfig;
import pe.rmsolutions.chatbot.sheets.WorkbookParser;
import pe.rmsolutions.chatbot.sheets.WorkbookProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre todos los casos de la sección 6.2 de la spec de la fase 1, usando el fixture
 * {@code workbook-fase1.json} como única fuente de datos.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class AccountStatusServiceTest {

    private AccountStatusService service;

    @BeforeEach
    void preparar() {
        SheetsConfig config = new SheetsConfig() {
            @Override
            public String spreadsheetId() {
                return "fake";
            }

            @Override
            public Optional<String> credentialsFile() {
                return Optional.empty();
            }

            @Override
            public Optional<String> credentialsJson() {
                return Optional.empty();
            }

            @Override
            public Duration cacheTtl() {
                return Duration.ofMinutes(1);
            }

            @Override
            public Duration staleMax() {
                return Duration.ofMinutes(30);
            }
        };

        WorkbookProvider provider = new WorkbookProvider(
                new FixtureWorkbookSource("fixtures/workbook-fase1.json"),
                new WorkbookParser(),
                config,
                Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));

        service = new AccountStatusService(provider);
    }

    @Test
    void gabyTieneDosCampaniasConCarmelPrimero() {
        AccountStatus status = service.getAccountStatus("51911111111");

        assertThat(status.registered()).isTrue();
        assertThat(status.customerName()).isEqualTo("Gaby");
        assertThat(status.campaigns()).hasSize(2);

        CampaignAccount carmel = status.campaigns().get(0);
        assertThat(carmel.campaignTitle()).isEqualTo("C-11-carmel");
        assertThat(carmel.paymentDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(carmel.groups()).hasSize(1);
        assertThat(carmel.groups().get(0).recipient()).isNull();
        assertThat(carmel.groups().get(0).items()).extracting("product", "amount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Pantalón", amount("59.90")),
                        org.assertj.core.groups.Tuple.tuple("Blusa crema", amount("19.90")));
        assertThat(carmel.total()).isEqualTo(amount("79.80"));

        CampaignAccount pacifika = status.campaigns().get(1);
        assertThat(pacifika.campaignTitle()).isEqualTo("C-11-pacifika");
        assertThat(pacifika.paymentDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(pacifika.groups()).hasSize(2);
        assertThat(pacifika.groups().get(0).recipient()).isNull();
        assertThat(pacifika.groups().get(0).subtotal()).isEqualTo(amount("19.90"));
        assertThat(pacifika.groups().get(1).recipient()).isEqualTo("Ana");
        assertThat(pacifika.groups().get(1).items()).extracting("product")
                .containsExactly("Vestido lila", "Ropa de baño");
        assertThat(pacifika.groups().get(1).subtotal()).isEqualTo(amount("75.83"));
        assertThat(pacifika.soldOut()).extracting("product", "size")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Vestido azul", "S"));
        assertThat(pacifika.unpriced()).isEmpty();
        assertThat(pacifika.total()).isEqualTo(amount("95.73"));

        assertThat(status.grandTotal()).isEqualTo(amount("175.53"));
    }

    @Test
    void celularConFormatoAlternativoDevuelveLoMismoQueGaby() {
        AccountStatus status = service.getAccountStatus("+51 911 111 111");

        assertThat(status.registered()).isTrue();
        assertThat(status.customerName()).isEqualTo("Gaby");
        assertThat(status.grandTotal()).isEqualTo(amount("175.53"));
    }

    @Test
    void rogerTieneConjuntoNegroYBufandaSinC10() {
        AccountStatus status = service.getAccountStatus("51933333333");

        assertThat(status.customerName()).isEqualTo("Roger");
        assertThat(status.campaigns()).hasSize(2);

        CampaignAccount pacifika = status.campaigns().get(0);
        assertThat(pacifika.campaignTitle()).isEqualTo("C-11-pacifika");
        assertThat(pacifika.paymentDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(pacifika.groups()).extracting("recipient").containsExactly((Object) null);
        assertThat(pacifika.groups().get(0).items()).extracting("product")
                .containsExactly("Conjunto negro");
        assertThat(pacifika.unpriced()).extracting("product", "size")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Chompa", "M"));
        assertThat(pacifika.total()).isEqualTo(amount("71.91"));

        CampaignAccount carmel12 = status.campaigns().get(1);
        assertThat(carmel12.campaignTitle()).isEqualTo("C-12-Carmel");
        assertThat(carmel12.paymentDate()).isNull();
        assertThat(carmel12.total()).isEqualTo(amount("15.00"));

        assertThat(status.grandTotal()).isEqualTo(amount("86.91"));
    }

    @Test
    void marialeSeResuelveConLaOrtografiaDeClientasYSoloTieneUnaCampania() {
        AccountStatus status = service.getAccountStatus("51922222222");

        assertThat(status.customerName()).isEqualTo("Mariale R.");
        assertThat(status.campaigns()).hasSize(1);

        CampaignAccount pacifika = status.campaigns().get(0);
        assertThat(pacifika.campaignTitle()).isEqualTo("C-11-pacifika");
        assertThat(pacifika.groups().get(0).items()).extracting("product")
                .containsExactly("Polo rosa");
        assertThat(pacifika.unpriced()).extracting("product", "size")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Enterizo negro", "S"));
        assertThat(pacifika.total()).isEqualTo(amount("25.00"));

        assertThat(status.grandTotal()).isEqualTo(amount("25.00"));
    }

    @Test
    void josePerezSeResuelveConLaOrtografiaDeClientas() {
        AccountStatus status = service.getAccountStatus("51944444444");

        assertThat(status.customerName()).isEqualTo("José Pérez");
        assertThat(status.campaigns()).hasSize(1);
        assertThat(status.campaigns().get(0).groups().get(0).items()).extracting("product")
                .containsExactly("Casaca");
        assertThat(status.grandTotal()).isEqualTo(amount("89.91"));
    }

    @Test
    void luciaEstaRegistradaPeroSinCampaniasPropias() {
        AccountStatus status = service.getAccountStatus("51955555555");

        assertThat(status.registered()).isTrue();
        assertThat(status.campaigns()).isEmpty();
        assertThat(status.grandTotal()).isEqualTo(ZERO);
    }

    @Test
    void anaTorresEstaRegistradaConCelularConGuionesYSinCampanias() {
        AccountStatus status = service.getAccountStatus("966666666");

        assertThat(status.registered()).isTrue();
        assertThat(status.customerName()).isEqualTo("Ana Torres");
        assertThat(status.campaigns()).isEmpty();
        assertThat(status.grandTotal()).isEqualTo(ZERO);
    }

    @Test
    void celularNoRegistradoDevuelveNoRegistrada() {
        AccountStatus status = service.getAccountStatus("51999999999");

        assertThat(status.registered()).isFalse();
        assertThat(status.customerName()).isNull();
        assertThat(status.campaigns()).isEmpty();
        assertThat(status.grandTotal()).isEqualTo(ZERO);
    }

    @Test
    void celularInvalidoDevuelveNoRegistrada() {
        AccountStatus status = service.getAccountStatus("12345");

        assertThat(status.registered()).isFalse();
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
