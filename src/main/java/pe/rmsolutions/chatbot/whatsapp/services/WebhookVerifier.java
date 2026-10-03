package pe.rmsolutions.chatbot.whatsapp.services;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.rmsolutions.chatbot.whatsapp.config.WhatsAppConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Verifica la suscripción del webhook que Meta solicita al configurarlo.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class WebhookVerifier {

    static final String SUBSCRIBE_MODE = "subscribe";

    private final WhatsAppConfig config;

    /**
     * @return el {@code hub.challenge} tal cual, si el modo es {@code subscribe} y el token coincide
     * @throws WebhookVerificationException en cualquier otro caso
     */
    public String verify(String mode, String verifyToken, String challenge) {
        if (!SUBSCRIBE_MODE.equals(mode) || verifyToken == null || challenge == null
                || !MessageDigest.isEqual(verifyToken.getBytes(StandardCharsets.UTF_8),
                config.verifyToken().getBytes(StandardCharsets.UTF_8))) {
            log.warn("Verificación del webhook rechazada");
            throw new WebhookVerificationException();
        }
        log.info("Webhook de WhatsApp verificado");
        return challenge;
    }
}
