package pe.rmsolutions.chatbot.observability.services;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.observability.services.BotMetrics.IgnoreReason;
import pe.rmsolutions.chatbot.observability.services.BotMetrics.TurnOutcome;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cada punto de registro incrementa su métrica, con atributos que no llevan datos personales.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class BotMetricsTest {

    private final InMemoryMetricReader reader = InMemoryMetricReader.create();
    private final SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    private final BotMetrics metrics = new BotMetrics(provider.get("test"));

    @AfterEach
    void close() {
        provider.close();
    }

    @Test
    void turnosPorResultadoYDuracion() {
        metrics.turn(TurnOutcome.REPLIED);
        metrics.turn(TurnOutcome.REPLIED);
        metrics.turn(TurnOutcome.PAUSED);
        metrics.turn(TurnOutcome.ERROR);
        metrics.turnDuration(1200);

        Collection<MetricData> data = reader.collectAllMetrics();

        assertThat(counts(data, "chatbot.turns", BotMetrics.OUTCOME.getKey()))
                .containsExactlyInAnyOrderEntriesOf(Map.of("replied", 2L, "paused", 1L, "error", 1L));
        assertThat(metric(data, "chatbot.turn.duration").getHistogramData().getPoints())
                .singleElement().satisfies(p -> {
                    assertThat(p.getCount()).isEqualTo(1);
                    assertThat(p.getSum()).isEqualTo(1200.0);
                });
        assertThat(metric(data, "chatbot.turn.duration").getUnit()).isEqualTo("ms");
    }

    @Test
    void contadoresSinAtributos() {
        metrics.guardTriggered();
        metrics.handoff();
        metrics.handoff();
        metrics.nonTextMessage();
        metrics.invalidSignature();

        Collection<MetricData> data = reader.collectAllMetrics();

        assertThat(total(data, "chatbot.guard.triggered")).isEqualTo(1);
        assertThat(total(data, "chatbot.handoffs")).isEqualTo(2);
        assertThat(total(data, "chatbot.messages.non_text")).isEqualTo(1);
        assertThat(total(data, "chatbot.webhook.signature_invalid")).isEqualTo(1);
    }

    @Test
    void mensajesIgnoradosPorMotivo() {
        metrics.messageIgnored(IgnoreReason.DISABLED);
        metrics.messageIgnored(IgnoreReason.NOT_ALLOWLISTED);
        metrics.messageIgnored(IgnoreReason.NOTICE_SUPPRESSED);
        metrics.messageIgnored(IgnoreReason.NOTICE_SUPPRESSED);

        assertThat(counts(reader.collectAllMetrics(), "chatbot.messages.ignored", BotMetrics.REASON.getKey()))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("disabled", 1L, "not_allowlisted", 1L, "notice_suppressed", 2L));
    }

    @Test
    void erroresDeEnvioPorCodigoDeMeta() {
        metrics.outboundError(131030);
        metrics.outboundError(null);

        assertThat(counts(reader.collectAllMetrics(), "chatbot.outbound.errors", BotMetrics.META_ERROR_CODE.getKey()))
                .containsExactlyInAnyOrderEntriesOf(Map.of("131030", 1L, "none", 1L));
    }

    @Test
    void ningunAtributoLlevaDatosPersonales() {
        metrics.turn(TurnOutcome.REPLIED);
        metrics.messageIgnored(IgnoreReason.NOT_ALLOWLISTED);
        metrics.outboundError(131030);

        assertThat(reader.collectAllMetrics()).allSatisfy(metric -> assertThat(metric.getData().getPoints())
                .allSatisfy(point -> point.getAttributes().forEach((key, value) -> {
                    assertThat(key.getKey()).isIn("outcome", "reason", "metaErrorCode");
                    assertThat(value.toString()).doesNotMatch(".*\\d{9}.*");
                })));
    }

    private static MetricData metric(Collection<MetricData> data, String name) {
        return data.stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    }

    private static long total(Collection<MetricData> data, String name) {
        return metric(data, name).getLongSumData().getPoints().stream().mapToLong(LongPointData::getValue).sum();
    }

    private static Map<String, Long> counts(Collection<MetricData> data, String name, String attribute) {
        return metric(data, name).getLongSumData().getPoints().stream().collect(Collectors.toMap(
                p -> p.getAttributes().get(AttributeKey.stringKey(attribute)),
                LongPointData::getValue));
    }
}
