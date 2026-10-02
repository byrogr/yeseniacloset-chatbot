package pe.rmsolutions.chatbot.account.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estado de cuenta completo de una clienta: sus campañas incluidas y el total general.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record AccountStatus(
        boolean registered,
        String customerName,
        List<CampaignAccount> campaigns,
        BigDecimal grandTotal) {
}
