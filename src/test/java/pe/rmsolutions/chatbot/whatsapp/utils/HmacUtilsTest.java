package pe.rmsolutions.chatbot.whatsapp.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el cálculo HMAC-SHA256 contra un vector conocido (RFC 4231, caso 2).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class HmacUtilsTest {

    private static final byte[] DATA = "what do ya want for nothing?".getBytes(StandardCharsets.UTF_8);
    private static final String EXPECTED = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";

    @Test
    void calculaHexMinusculo() {
        assertThat(HmacUtils.hmacSha256Hex("Jefe", DATA)).isEqualTo(EXPECTED);
    }

    @Test
    void validaElHeaderConPrefijo() {
        assertThat(HmacUtils.isValidSignature("Jefe", DATA, "sha256=" + EXPECTED)).isTrue();
        assertThat(HmacUtils.isValidSignature("Jefe", DATA, "sha256=" + EXPECTED.toUpperCase())).isFalse();
        assertThat(HmacUtils.isValidSignature("Jefe", DATA, EXPECTED)).isFalse();
        assertThat(HmacUtils.isValidSignature("Jefe", DATA, null)).isFalse();
        assertThat(HmacUtils.isValidSignature("otro", DATA, "sha256=" + EXPECTED)).isFalse();
    }
}
