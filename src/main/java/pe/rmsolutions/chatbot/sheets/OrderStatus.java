package pe.rmsolutions.chatbot.sheets;

/**
 * Estado de una fila de pedido en una pestaña de campaña. Los nombres de las constantes
 * reproducen el texto exacto que las clientas ven en la columna {@code Estado} del Sheet.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public enum OrderStatus {
    PENDIENTE,
    ENTREGADO,
    PAGADO,
    AGOTADO,
    CANCELADO
}
