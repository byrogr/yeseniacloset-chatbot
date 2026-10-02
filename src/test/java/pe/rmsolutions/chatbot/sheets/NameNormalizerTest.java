package pe.rmsolutions.chatbot.sheets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class NameNormalizerTest {

    @Test
    void normalizaVariantesDeMarialeR() {
        assertThat(NameNormalizer.key("MARIALE R.")).isEqualTo("mariale r");
        assertThat(NameNormalizer.key("mariale r")).isEqualTo("mariale r");
        assertThat(NameNormalizer.key(" Mariale  R. ")).isEqualTo("mariale r");
    }

    @Test
    void normalizaVariantesDeJosePerez() {
        assertThat(NameNormalizer.key("Jose Perez")).isEqualTo("jose perez");
        assertThat(NameNormalizer.key("José Pérez")).isEqualTo("jose perez");
    }
}
