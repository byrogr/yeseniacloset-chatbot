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
 * <p>No se prueba {@code GET /api/v1/customers/{phone}/account-status} por HTTP porque el recurso
 * está anotado con {@code @IfBuildProfile("dev")} y el perfil activo durante los tests es
 * {@code test}; la propia spec (sección 6.4) indica probar el servicio inyectado en ese caso.
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
