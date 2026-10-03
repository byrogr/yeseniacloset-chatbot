package pe.rmsolutions.chatbot.agent.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class MaskingUtilsTest {

    @Test
    void enmascaraElCentroDelCelular() {
        assertThat(MaskingUtils.maskPhone("51987654321")).isEqualTo("51987***321");
    }

    @Test
    void valoresCortosONulosSeOcultanCompletos() {
        assertThat(MaskingUtils.maskPhone("12345")).isEqualTo("***");
        assertThat(MaskingUtils.maskPhone(null)).isEqualTo("***");
    }
}
