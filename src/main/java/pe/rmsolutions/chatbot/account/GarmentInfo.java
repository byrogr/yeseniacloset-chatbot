package pe.rmsolutions.chatbot.account;

/**
 * Referencia liviana a una prenda sin monto que suma (agotada o cobrable sin precio).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record GarmentInfo(String product, String size, String recipient) {
}
