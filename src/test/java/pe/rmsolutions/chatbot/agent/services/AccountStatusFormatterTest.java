package pe.rmsolutions.chatbot.agent.services;

import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.account.model.AccountStatus;
import pe.rmsolutions.chatbot.account.services.AccountStatusService;
import pe.rmsolutions.chatbot.agent.FixtureAccounts;
import pe.rmsolutions.chatbot.agent.TestBotConfig;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compara el texto completo del formateador contra el fixture de la Fase 1.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class AccountStatusFormatterTest {

    private final AccountStatusService service = FixtureAccounts.service();
    private final AccountStatusFormatter formatter = new AccountStatusFormatter(new HandoffMessages(new TestBotConfig()));

    @Test
    void gaby() {
        String expected = """
                Hola Gaby, este es tu pedido:

                C-11 Carmel (pagar hasta el 20/10/2026)
                Pantalón M: S/ 59,90
                Blusa crema M: S/ 19,90
                Total: S/ 79,80

                C-11 Pacifika (pagar hasta el 15/10/2026)
                Tuyas:
                Blusa blanca M: S/ 19,90
                Ana:
                Vestido lila M: S/ 35,91
                Ropa de baño M: S/ 39,92
                Agotado (no se te cobra): Vestido azul S
                Total: S/ 95,73

                Total general: S/ 175,53
                Si ya hiciste algún pago o adelanto, Yesenia lo descuenta.""";

        assertThat(formatter.format(service.getAccountStatus(FixtureAccounts.GABY))).isEqualTo(expected);
    }

    @Test
    void rogerConPrendaSinPrecioYFechaNula() {
        String expected = """
                Hola Roger, este es tu pedido:

                C-11 Pacifika (pagar hasta el 15/10/2026)
                Conjunto negro M: S/ 71,91
                Chompa M: precio por confirmar
                Total: S/ 71,91

                C-12 Carmel (fecha de pago por confirmar)
                Bufanda U: S/ 15,00
                Total: S/ 15,00

                Total general: S/ 86,91
                Si ya hiciste algún pago o adelanto, Yesenia lo descuenta.""";

        assertThat(formatter.format(service.getAccountStatus(FixtureAccounts.ROGER))).isEqualTo(expected);
    }

    @Test
    void noRegistrada() {
        assertThat(formatter.format(service.getAccountStatus(FixtureAccounts.UNREGISTERED)))
                .isEqualTo("Hola, Yesenia te escribirá pronto para ayudarte con tu pedido.");
    }

    @Test
    void noRegistradaConNumeroNuevoMandaAlChatDeLaDuenia() {
        var redirecting = new AccountStatusFormatter(new HandoffMessages(TestBotConfig.redirectingToOwner()));

        assertThat(redirecting.format(service.getAccountStatus(FixtureAccounts.UNREGISTERED)))
                .isEqualTo("No encuentro pedidos con este número. Escríbele a Yesenia: https://wa.me/51911222333");
    }

    @Test
    void sinCampanias() {
        var status = new AccountStatus(true, "Lucía", List.of(), new BigDecimal("0.00"));

        assertThat(formatter.format(status))
                .isEqualTo("Hola Lucía, no tienes pedidos pendientes por ahora.");
    }
}
