package pe.rmsolutions.chatbot.agent.services;

import jakarta.enterprise.context.ApplicationScoped;
import pe.rmsolutions.chatbot.account.model.AccountStatus;
import pe.rmsolutions.chatbot.account.model.CampaignAccount;
import pe.rmsolutions.chatbot.account.model.Garment;
import pe.rmsolutions.chatbot.account.model.GarmentInfo;
import pe.rmsolutions.chatbot.account.model.RecipientGroup;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Redacta el estado de cuenta de forma determinista, sin LLM. Es el respaldo cuando la guardia
 * de montos rechaza la respuesta del modelo.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
public class AccountStatusFormatter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String format(AccountStatus status, String ownerName) {
        if (!status.registered()) {
            return "Hola, " + ownerName + " te escribirá pronto para ayudarte con tu pedido.";
        }
        if (status.campaigns().isEmpty()) {
            return "Hola " + status.customerName() + ", no tienes pedidos pendientes por ahora.";
        }

        StringBuilder text = new StringBuilder("Hola ").append(status.customerName()).append(", este es tu pedido:\n");
        for (CampaignAccount campaign : status.campaigns()) {
            text.append('\n');
            appendCampaign(text, campaign);
        }
        text.append('\n')
                .append("Total general: ").append(money(status.grandTotal())).append('\n')
                .append("Si ya hiciste algún pago o adelanto, ").append(ownerName).append(" lo descuenta.");
        return text.toString();
    }

    private void appendCampaign(StringBuilder text, CampaignAccount campaign) {
        text.append("C-").append(campaign.number()).append(' ').append(capitalize(campaign.catalog()))
                .append(campaign.paymentDate() == null
                        ? " (fecha de pago por confirmar)"
                        : " (pagar hasta el " + DATE.format(campaign.paymentDate()) + ")")
                .append('\n');

        boolean labelGroups = campaign.groups().stream().anyMatch(g -> g.recipient() != null);
        for (RecipientGroup group : campaign.groups()) {
            if (labelGroups) {
                text.append(group.recipient() == null ? "Tuyas" : group.recipient()).append(":\n");
            }
            for (Garment garment : group.items()) {
                text.append(describe(garment.product(), garment.size())).append(": ")
                        .append(money(garment.amount())).append('\n');
            }
        }
        for (GarmentInfo unpriced : campaign.unpriced()) {
            text.append(describe(unpriced.product(), unpriced.size())).append(": precio por confirmar\n");
        }
        if (!campaign.soldOut().isEmpty()) {
            text.append("Agotado (no se te cobra): ").append(describeAll(campaign.soldOut())).append('\n');
        }
        text.append("Total: ").append(money(campaign.total())).append('\n');
    }

    private static String describeAll(List<GarmentInfo> garments) {
        return garments.stream().map(g -> describe(g.product(), g.size())).collect(Collectors.joining(", "));
    }

    private static String describe(String product, String size) {
        return size == null || size.isBlank() ? product : product + " " + size;
    }

    private static String money(BigDecimal amount) {
        return "S/ " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static String capitalize(String catalog) {
        String lower = catalog.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
