package pe.rmsolutions.chatbot.whatsapp.services;

/**
 * La verificación de la suscripción del webhook falló ({@code hub.mode} o {@code hub.verify_token} incorrectos).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public class WebhookVerificationException extends RuntimeException {

    public WebhookVerificationException() {
        super("Verificación del webhook fallida");
    }
}
