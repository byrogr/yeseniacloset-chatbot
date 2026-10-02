package pe.rmsolutions.chatbot.account;

import pe.rmsolutions.chatbot.sheets.OrderStatus;

import java.math.BigDecimal;

/**
 * Una prenda cobrable (Pendiente o Entregado) con precio, dentro de un {@link RecipientGroup}.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record Garment(String product, String size, BigDecimal amount, OrderStatus status) {
}
