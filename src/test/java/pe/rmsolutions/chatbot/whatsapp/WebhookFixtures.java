package pe.rmsolutions.chatbot.whatsapp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Lee los payloads de ejemplo del webhook desde {@code fixtures/webhook}.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public final class WebhookFixtures {

    private WebhookFixtures() {
    }

    public static String read(String name) {
        try (InputStream in = WebhookFixtures.class.getResourceAsStream("/fixtures/webhook/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture inexistente: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
