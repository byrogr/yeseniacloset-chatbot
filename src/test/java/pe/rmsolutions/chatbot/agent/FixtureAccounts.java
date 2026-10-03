package pe.rmsolutions.chatbot.agent;

import pe.rmsolutions.chatbot.account.services.AccountStatusService;
import pe.rmsolutions.chatbot.sheets.config.SheetsConfig;
import pe.rmsolutions.chatbot.sheets.repository.FixtureWorkbookSource;
import pe.rmsolutions.chatbot.sheets.services.WorkbookParser;
import pe.rmsolutions.chatbot.sheets.services.WorkbookProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Construye un {@link AccountStatusService} real sobre el fixture de la Fase 1, sin CDI.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public final class FixtureAccounts {

    public static final String GABY = "51911111111";
    public static final String MARIALE = "51922222222";
    public static final String ROGER = "51933333333";
    public static final String UNREGISTERED = "51999999999";

    private FixtureAccounts() {
    }

    public static AccountStatusService service() {
        SheetsConfig config = new SheetsConfig() {
            @Override
            public String spreadsheetId() {
                return "fixture";
            }

            @Override
            public Optional<String> credentialsFile() {
                return Optional.empty();
            }

            @Override
            public Optional<String> credentialsJson() {
                return Optional.empty();
            }

            @Override
            public Duration cacheTtl() {
                return Duration.ofMinutes(1);
            }

            @Override
            public Duration staleMax() {
                return Duration.ofMinutes(30);
            }
        };
        WorkbookProvider provider = new WorkbookProvider(
                new FixtureWorkbookSource(), new WorkbookParser(), config,
                Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
        return new AccountStatusService(provider);
    }
}
