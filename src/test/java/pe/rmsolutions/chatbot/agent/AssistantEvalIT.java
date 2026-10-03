package pe.rmsolutions.chatbot.agent;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import pe.rmsolutions.chatbot.agent.model.ChatReply;
import pe.rmsolutions.chatbot.agent.repository.ExpiringChatMemoryStore;
import pe.rmsolutions.chatbot.agent.services.ChatService;
import pe.rmsolutions.chatbot.agent.services.ConversationPauseRegistry;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evalúa el asistente contra el modelo real de Foundry con el set {@code eval/fase2-frases.yaml} y el
 * fixture de la Fase 1. Solo corre con {@code ./mvnw verify -Peval} y las variables {@code FOUNDRY_*}.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@QuarkusTest
@Tag("eval")
class AssistantEvalIT {

    private static final int MAX_GUARD_TRIGGERS = 2;
    private static final long MAX_P95_MS = 6_000;

    @Inject
    ChatService chatService;
    @Inject
    ExpiringChatMemoryStore memoryStore;
    @Inject
    ConversationPauseRegistry pauses;

    @Test
    void evaluaElSetDeFrases() {
        List<Outcome> outcomes = new ArrayList<>();
        for (EvalCase evalCase : loadCases()) {
            memoryStore.deleteMessages(evalCase.phone());
            pauses.resume(evalCase.phone());

            long start = System.nanoTime();
            ChatReply reply = chatService.reply(evalCase.phone(), evalCase.message());
            long latencyMs = (System.nanoTime() - start) / 1_000_000;

            String text = reply.text().orElse("");
            String firstTool = reply.toolsUsed().isEmpty() ? "NONE" : toolCode(reply.toolsUsed().getFirst());
            List<String> leaked = evalCase.forbidden().stream().filter(text::contains).toList();
            boolean ok = evalCase.expect().contains(firstTool) && leaked.isEmpty();
            String tools = reply.toolsUsed().isEmpty() ? "NONE"
                    : String.join(">", reply.toolsUsed().stream().map(AssistantEvalIT::toolCode).toList());
            outcomes.add(new Outcome(evalCase, firstTool, tools, leaked, reply.guardTriggered(), reply.paused(),
                    latencyMs, ok, text));
        }

        List<Outcome> failed = outcomes.stream().filter(o -> !o.ok()).toList();
        long guardTriggers = outcomes.stream().filter(Outcome::guardTriggered).count();
        List<Long> latencies = outcomes.stream().map(Outcome::latencyMs).sorted(Comparator.naturalOrder()).toList();
        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);

        printSummary(outcomes, failed, guardTriggers, p50, p95);

        assertThat(failed).as("casos con tool no aceptable o texto prohibido").isEmpty();
        assertThat(guardTriggers).as("activaciones de la guardia").isLessThanOrEqualTo(MAX_GUARD_TRIGGERS);
        assertThat(p95).as("latencia p95 (ms)").isLessThan(MAX_P95_MS);
    }

    private static String toolCode(String toolName) {
        return switch (toolName) {
            case "getAccountStatus" -> "ACCOUNT";
            case "handOffToOwner" -> "HANDOFF";
            default -> toolName;
        };
    }

    private static long percentile(List<Long> sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(index, 0));
    }

    private static void printSummary(List<Outcome> outcomes, List<Outcome> failed, long guardTriggers,
                                     long p50, long p95) {
        StringBuilder out = new StringBuilder("\n===== Evaluación Fase 2 =====\n");
        out.append(String.format("%-8s %-24s %-16s %-6s %-7s %8s%n",
                "caso", "esperado", "tools", "guard", "pausa", "ms"));
        for (Outcome o : outcomes) {
            out.append(String.format("%-8s %-24s %-16s %-6s %-7s %8d %s%n", o.evalCase().id(),
                    o.evalCase().expect(), o.tools(), o.guardTriggered() ? "sí" : "", o.paused() ? "sí" : "",
                    o.latencyMs(), o.ok() ? "" : "FALLA"));
        }
        out.append(String.format("%nAciertos: %d/%d%nGuardia activada: %d%nLatencia p50: %d ms · p95: %d ms%n",
                outcomes.size() - failed.size(), outcomes.size(), guardTriggers, p50, p95));
        for (Outcome o : failed) {
            out.append(String.format("%n--- %s (tool %s, prohibidos %s)%n%s%n",
                    o.evalCase().id(), o.firstTool(), o.leaked(), o.reply()));
        }
        System.out.println(out);
    }

    @SuppressWarnings("unchecked")
    private static List<EvalCase> loadCases() {
        try (InputStream in = AssistantEvalIT.class.getClassLoader().getResourceAsStream("eval/fase2-frases.yaml")) {
            Map<String, Object> root = new Yaml().load(in);
            List<Map<String, Object>> cases = (List<Map<String, Object>>) root.get("cases");
            return cases.stream().map(c -> new EvalCase(
                    (String) c.get("id"),
                    (String) c.get("phone"),
                    (String) c.get("message"),
                    (List<String>) c.get("expect"),
                    (List<String>) c.getOrDefault("forbidden", List.of()))).toList();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo leer eval/fase2-frases.yaml", e);
        }
    }

    private record EvalCase(String id, String phone, String message, List<String> expect, List<String> forbidden) {
    }

    private record Outcome(EvalCase evalCase, String firstTool, String tools, List<String> leaked,
                           boolean guardTriggered, boolean paused, long latencyMs, boolean ok, String reply) {
    }
}
