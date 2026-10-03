package pe.rmsolutions.chatbot.agent.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.account.model.AccountStatus;
import pe.rmsolutions.chatbot.agent.FixtureAccounts;
import pe.rmsolutions.chatbot.agent.model.GuardResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class AmountGuardTest {

    private final AmountGuard guard = new AmountGuard();
    private final AccountStatus gaby = FixtureAccounts.service().getAccountStatus(FixtureAccounts.GABY);

    @ParameterizedTest
    @ValueSource(strings = {
            "El vestido lila es S/ 35,91",
            "El vestido lila es S/.35.91",
            "El vestido lila es s/35,91",
            "El vestido lila es S/. 35.91",
            "Total S/ 175,53 y Carmel S/ 79,80",
            "Lo de Ana suma S/ 75,83"
    })
    void montosPermitidosEnVariosFormatos(String reply) {
        assertThat(guard.check(reply, gaby).status()).isEqualTo(GuardResult.Status.OK);
    }

    @Test
    void montoSinDecimalesSeNormaliza() {
        var status = FixtureAccounts.service().getAccountStatus(FixtureAccounts.ROGER);

        assertThat(guard.check("La bufanda es S/ 15", status).status()).isEqualTo(GuardResult.Status.OK);
    }

    @Test
    void montoInventadoEsViolacion() {
        GuardResult result = guard.check("Tu total es S/ 175,53, con descuento S/ 160,00", gaby);

        assertThat(result.status()).isEqualTo(GuardResult.Status.VIOLATION);
        assertThat(result.unknownAmounts()).containsExactly(new BigDecimal("160.00"));
    }

    @Test
    void montosSinStatusEsViolacion() {
        assertThat(guard.check("Tu total es S/ 175,53", null).status()).isEqualTo(GuardResult.Status.VIOLATION);
    }

    @Test
    void respuestaSinMontosEsOk() {
        assertThat(guard.check("Hola Gaby, Yesenia te escribirá pronto.", null).status())
                .isEqualTo(GuardResult.Status.OK);
        assertThat(guard.check("Tu pedido es de la campaña 11", gaby).status()).isEqualTo(GuardResult.Status.OK);
    }
}
