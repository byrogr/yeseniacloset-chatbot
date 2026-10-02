package pe.rmsolutions.chatbot.sheets.model;

/**
 * Clienta registrada en la pestaña {@code Clientas}, con el celular ya normalizado.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record Customer(String name, String phone) {
}
