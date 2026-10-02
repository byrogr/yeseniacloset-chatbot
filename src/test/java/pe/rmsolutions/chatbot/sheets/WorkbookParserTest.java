package pe.rmsolutions.chatbot.sheets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class WorkbookParserTest {

    private ParsedWorkbook workbook;

    @BeforeEach
    void cargarFixture() {
        RawWorkbook raw = new FixtureWorkbookSource("fixtures/workbook-fase1.json").read();
        workbook = new WorkbookParser().parse(raw);
    }

    @Test
    void parseaTodasLasClientas() {
        assertThat(workbook.customers()).hasSize(6);
        assertThat(workbook.customers())
                .extracting(Customer::name)
                .contains("Gaby", "Roger", "Mariale R.", "José Pérez", "Lucía", "Ana Torres");
    }

    @Test
    void produceExactamenteTresAdvertencias() {
        assertThat(workbook.warnings()).hasSize(3);
        assertThat(workbook.warnings())
                .anySatisfy(a -> assertThat(a).contains("C-11-pacifika").contains("fila 12").contains("Desconocida"))
                .anySatisfy(a -> assertThat(a).contains("C-11-pacifika").contains("fila 13").contains("Reservado"))
                .anySatisfy(a -> assertThat(a).contains("C-11-carmel").contains("Gaby")
                        .contains("20/10/2026").contains("25/10/2026"));
    }

    @Test
    void descartaFilasConClientaDesconocidaOEstadoInvalido() {
        assertThat(workbook.rows())
                .noneMatch(f -> "Desconocida".equals(f.customer()))
                .noneMatch(f -> "Cartera".equals(f.product()));
    }

    @Test
    void parseaFilaCortaConMontoNulo() {
        OrderRow chompa = workbook.rows().stream()
                .filter(f -> "Chompa".equals(f.product()))
                .findFirst()
                .orElseThrow();

        assertThat(chompa.totalAmount()).isNull();
        assertThat(chompa.paymentDate()).isNull();
        assertThat(chompa.status()).isEqualTo(OrderStatus.PENDIENTE);
        assertThat(chompa.customer()).isEqualTo("Roger");
    }

    @Test
    void resuelveClientaConLaOrtografiaDeClientas() {
        OrderRow poloRosa = workbook.rows().stream()
                .filter(f -> "Polo rosa".equals(f.product()))
                .findFirst()
                .orElseThrow();

        assertThat(poloRosa.customer()).isEqualTo("Mariale R.");

        OrderRow casacaJose = workbook.rows().stream()
                .filter(f -> "Casaca".equals(f.product()))
                .filter(f -> f.totalAmount() != null && f.totalAmount().compareTo(new BigDecimal("89.91")) == 0)
                .findFirst()
                .orElseThrow();

        assertThat(casacaJose.customer()).isEqualTo("José Pérez");
    }
}
