package pe.rmsolutions.chatbot.whatsapp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Firma payloads de prueba como lo haría Meta, con el {@code app-secret} del perfil {@code test}.
 * Se calcula aquí de forma independiente para no validar el código de producción contra sí mismo.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public final class WebhookSigner {

    public static final String TEST_APP_SECRET = "test-app-secret";

    private WebhookSigner() {
    }

    public static String sign(String body) {
        return sign(body.getBytes(StandardCharsets.UTF_8));
    }

    public static String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(TEST_APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
