package pe.rmsolutions.chatbot.sheets;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconoce y parsea los nombres de pestaña de campaña del Sheet.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public final class TabNamePolicy {

    public static final String CUSTOMERS_TAB = "Clientas";

    private static final Pattern CAMPAIGN = Pattern.compile(
            "^C-(\\d+)-(pacifika|carmel)$", Pattern.CASE_INSENSITIVE);

    public boolean isCampaign(String title) {
        return parse(title).isPresent();
    }

    public Optional<CampaignId> parse(String title) {
        if (title == null) {
            return Optional.empty();
        }
        String trimmed = title.trim();
        Matcher matcher = CAMPAIGN.matcher(trimmed);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int number = Integer.parseInt(matcher.group(1));
        String catalog = matcher.group(2).toLowerCase(Locale.ROOT);
        return Optional.of(new CampaignId(trimmed, number, catalog));
    }
}
