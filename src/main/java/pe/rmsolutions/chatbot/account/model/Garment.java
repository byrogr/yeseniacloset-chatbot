package pe.rmsolutions.chatbot.account.model;

import pe.rmsolutions.chatbot.sheets.model.OrderStatus;

import java.math.BigDecimal;

/**
 * Una prenda cobrable (Pendiente o Entregado) con precio, dentro de un {@link RecipientGroup}.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record Garment(String product, String size, BigDecimal amount, OrderStatus status) {
}
