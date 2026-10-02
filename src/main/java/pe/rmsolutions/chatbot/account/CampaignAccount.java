package pe.rmsolutions.chatbot.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Estado de cuenta de una clienta dentro de una campaña incluida (con al menos una fila cobrable).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record CampaignAccount(
        String campaignTitle,
        int number,
        String catalog,
        LocalDate paymentDate,
        List<RecipientGroup> groups,
        List<GarmentInfo> soldOut,
        List<GarmentInfo> unpriced,
        BigDecimal total) {
}
