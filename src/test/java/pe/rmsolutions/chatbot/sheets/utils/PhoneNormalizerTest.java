package pe.rmsolutions.chatbot.sheets.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class PhoneNormalizerTest {

    @Test
    void normalizaFormatosValidos() {
        assertThat(PhoneNormalizer.normalize("51911111111")).contains("51911111111");
        assertThat(PhoneNormalizer.normalize("+51 911 111 111")).contains("51911111111");
        assertThat(PhoneNormalizer.normalize("911111111")).contains("51911111111");
        assertThat(PhoneNormalizer.normalize("51 966-666-666")).contains("51966666666");
    }

    @Test
    void rechazaFormatosInvalidos() {
        assertThat(PhoneNormalizer.normalize("12345")).isEmpty();
        assertThat(PhoneNormalizer.normalize("811111111")).isEmpty();
    }
}
