package pe.rmsolutions.chatbot.whatsapp.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import pe.rmsolutions.chatbot.agent.utils.MaskingUtils;
import pe.rmsolutions.chatbot.observability.services.BotMetrics;
import pe.rmsolutions.chatbot.whatsapp.config.WhatsAppConfig;

import java.time.temporal.ChronoUnit;

/**
 * Envía textos con la WhatsApp Cloud API directa. Un 5xx o un timeout se reintenta una vez tras 2 s;
 * un 4xx no se reintenta y se registra con el {@code error.code} de Meta. Cada intento fallido suma a
 * {@code chatbot.outbound.errors}.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@Slf4j
public class CloudApiMessenger implements OutboundMessenger {

    private final GraphApiClient client;
    private final WhatsAppConfig config;
    private final BotMetrics metrics;

    @Inject
    public CloudApiMessenger(@RestClient GraphApiClient client, WhatsAppConfig config, BotMetrics metrics) {
        this.client = client;
        this.config = config;
        this.metrics = metrics;
    }

    @Override
    @Retry(maxRetries = 1, delay = 2, delayUnit = ChronoUnit.SECONDS, jitter = 0,
            retryOn = {GraphApiServerException.class, ProcessingException.class})
    public void sendText(String to, String body) {
        try {
            client.sendMessage(config.apiVersion(), config.phoneNumberId(), "Bearer " + config.accessToken(),
                    GraphTextMessage.of(to, body));
        } catch (GraphApiClientException e) {
            log.warn("Envío rechazado por Meta para {}: status={} error.code={}",
                    MaskingUtils.maskPhone(to), e.getStatus(), e.getErrorCode());
            metrics.outboundError(e.getErrorCode());
            throw e;
        } catch (RuntimeException e) {
            metrics.outboundError(null);
            throw e;
        }
    }
}
