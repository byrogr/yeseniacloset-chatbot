package pe.rmsolutions.chatbot.account.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Prendas con precio agrupadas por el campo "Para" de la fila (o el grupo propio, si {@code recipient} es null).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record RecipientGroup(String recipient, List<Garment> items, BigDecimal subtotal) {
}
