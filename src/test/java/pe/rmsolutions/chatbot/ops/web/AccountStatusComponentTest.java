package pe.rmsolutions.chatbot.ops.web;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.account.services.AccountStatusService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de componente (sección 6.4 de la spec): verifica el cableado CDI completo
 * ({@link pe.rmsolutions.chatbot.sheets.repository.FixtureWorkbookSource} &rarr; {@code WorkbookParser}
 * &rarr; {@code WorkbookProvider} &rarr; {@link AccountStatusService}) en el perfil {@code test}.
 *
 * <p>El endpoint HTTP se prueba en {@link CustomerResourceTest}.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@QuarkusTest
class AccountStatusComponentTest {

    @Inject
    AccountStatusService service;

    @Test
    void elServicioInyectadoUsaElFixtureYCalculaElTotalDeGaby() {
        var status = service.getAccountStatus("51911111111");

        assertThat(status.registered()).isTrue();
        assertThat(status.customerName()).isEqualTo("Gaby");
        assertThat(status.grandTotal()).isEqualTo(new BigDecimal("175.53"));
    }
}
