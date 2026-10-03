package pe.rmsolutions.chatbot.agent.services;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.account.services.AccountStatusService;
import pe.rmsolutions.chatbot.agent.FixtureAccounts;
import pe.rmsolutions.chatbot.agent.MutableClock;
import pe.rmsolutions.chatbot.agent.TestBotConfig;
import pe.rmsolutions.chatbot.sheets.config.SheetsConfig;
import pe.rmsolutions.chatbot.sheets.repository.FailingWorkbookSource;
import pe.rmsolutions.chatbot.sheets.services.WorkbookParser;
import pe.rmsolutions.chatbot.sheets.services.WorkbookProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class OrderToolsTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-02T10:00:00Z"));
    private final TurnContext turnContext = new TurnContext();
    private final ConversationPauseRegistry pauses = new ConversationPauseRegistry(clock);

    @Test
    void getAccountStatusUsaElCelularDeLaMemoriaYGuardaEnElTurno() {
        OrderTools tools = tools(FixtureAccounts.service());

        String json = tools.getAccountStatus(FixtureAccounts.GABY);

        assertThat(json).contains("\"customerName\":\"Gaby\"")
                .contains("\"grandTotal\":\"175.53\"")
                .contains("\"amount\":\"35.91\"")
                .contains("\"paymentDate\":\"2026-10-20\"")
                .doesNotContain("Mariale");
        assertThat(turnContext.get(FixtureAccounts.GABY)).get()
                .satisfies(s -> assertThat(s.customerName()).isEqualTo("Gaby"));
        assertThat(turnContext.get(FixtureAccounts.MARIALE)).isEmpty();
    }

    @Test
    void libroNoDisponibleDevuelveAvailableFalse() {
        OrderTools tools = tools(unavailableService());

        assertThat(tools.getAccountStatus(FixtureAccounts.GABY)).isEqualTo("{\"available\":false}");
        assertThat(turnContext.get(FixtureAccounts.GABY)).isEmpty();
    }

    @Test
    void handOffToOwnerPausaLaConversacion() {
        OrderTools tools = tools(FixtureAccounts.service());

        assertThat(tools.handOffToOwner(FixtureAccounts.GABY)).isEqualTo("{\"handedOff\":true}");

        assertThat(pauses.pausedUntil(FixtureAccounts.GABY)).contains(clock.instant().plus(TestBotConfig.PAUSE));
    }

    @Test
    void ningunaToolRecibeIdentidadComoParametroDelModelo() {
        for (Method method : OrderTools.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Tool.class)) {
                continue;
            }
            Parameter[] params = method.getParameters();
            assertThat(params[0].isAnnotationPresent(ToolMemoryId.class))
                    .as("%s recibe el celular por @ToolMemoryId", method.getName()).isTrue();
            assertThat(Arrays.stream(params).skip(1).filter(p -> p.isAnnotationPresent(ToolMemoryId.class)))
                    .isEmpty();
            assertThat(Arrays.stream(params).skip(1).map(p -> p.getAnnotation(P.class).value()))
                    .noneMatch(d -> d.toLowerCase().contains("phone") || d.toLowerCase().contains("customer"));
        }
    }

    private OrderTools tools(AccountStatusService service) {
        return new OrderTools(service, turnContext, pauses, new TestBotConfig());
    }

    private AccountStatusService unavailableService() {
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
        return new AccountStatusService(
                new WorkbookProvider(new FailingWorkbookSource(), new WorkbookParser(), config, clock));
    }
}
