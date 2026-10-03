package pe.rmsolutions.chatbot.agent.services;

import jakarta.enterprise.context.ApplicationScoped;
import pe.rmsolutions.chatbot.account.model.AccountStatus;
import pe.rmsolutions.chatbot.account.model.CampaignAccount;
import pe.rmsolutions.chatbot.account.model.Garment;
import pe.rmsolutions.chatbot.account.model.RecipientGroup;
import pe.rmsolutions.chatbot.agent.model.GuardResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprueba que todo monto en soles de una respuesta del modelo exista en el {@link AccountStatus}
 * del turno. Es la red de seguridad del principio "los montos nunca los inventa el LLM".
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
public class AmountGuard {

    private static final Pattern AMOUNT = Pattern.compile("(?i)s/\\.?\\s*(\\d+(?:[.,]\\d{1,2})?)");

    public GuardResult check(String reply, AccountStatus status) {
        Set<BigDecimal> found = extractAmounts(reply);
        if (found.isEmpty()) {
            return new GuardResult(GuardResult.Status.OK, Set.of());
        }
        if (status == null) {
            return new GuardResult(GuardResult.Status.VIOLATION, found);
        }

        Set<BigDecimal> allowed = allowedAmounts(status);
        Set<BigDecimal> unknown = new LinkedHashSet<>(found);
        unknown.removeAll(allowed);
        return unknown.isEmpty()
                ? new GuardResult(GuardResult.Status.OK, Set.of())
                : new GuardResult(GuardResult.Status.VIOLATION, unknown);
    }

    private Set<BigDecimal> extractAmounts(String reply) {
        Set<BigDecimal> amounts = new LinkedHashSet<>();
        if (reply == null) {
            return amounts;
        }
        Matcher matcher = AMOUNT.matcher(reply);
        while (matcher.find()) {
            amounts.add(scale(new BigDecimal(matcher.group(1).replace(',', '.'))));
        }
        return amounts;
    }

    private Set<BigDecimal> allowedAmounts(AccountStatus status) {
        Set<BigDecimal> allowed = new HashSet<>();
        allowed.add(scale(status.grandTotal()));
        for (CampaignAccount campaign : status.campaigns()) {
            allowed.add(scale(campaign.total()));
            for (RecipientGroup group : campaign.groups()) {
                allowed.add(scale(group.subtotal()));
                for (Garment garment : group.items()) {
                    allowed.add(scale(garment.amount()));
                }
            }
        }
        return allowed;
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
