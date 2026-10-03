package pe.rmsolutions.chatbot.whatsapp.utils;

import lombok.experimental.UtilityClass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Firma HMAC-SHA256 del webhook de Meta ({@code X-Hub-Signature-256: sha256=<hex>}).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@UtilityClass
public class HmacUtils {

    public static final String SIGNATURE_PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    /**
     * HMAC-SHA256 de los bytes exactos del cuerpo, en hexadecimal minúsculo.
     */
    public static String hmacSha256Hex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);
        }
    }

    /**
     * Compara en tiempo constante la firma recibida en el header con la calculada sobre el cuerpo.
     * Un header ausente o sin el prefijo {@code sha256=} nunca coincide.
     */
    public static boolean isValidSignature(String secret, byte[] body, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        byte[] expected = hmacSha256Hex(secret, body).getBytes(StandardCharsets.US_ASCII);
        byte[] received = signatureHeader.substring(SIGNATURE_PREFIX.length()).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, received);
    }
}
