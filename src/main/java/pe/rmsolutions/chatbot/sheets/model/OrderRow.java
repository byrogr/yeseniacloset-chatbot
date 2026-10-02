package pe.rmsolutions.chatbot.sheets.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila ya parseada de una pestaña de campaña.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record OrderRow(
        CampaignId campaign,
        int sheetRow,
        String product,
        String size,
        String customer,
        String recipient,
        LocalDate paymentDate,
        OrderStatus status,
        BigDecimal totalAmount) {
}
