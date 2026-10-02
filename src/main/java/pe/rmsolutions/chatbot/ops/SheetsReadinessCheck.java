package pe.rmsolutions.chatbot.ops;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import pe.rmsolutions.chatbot.sheets.WorkbookProvider;

/**
 * Reporta DOWN solo cuando no hay ninguna carga válida del Sheet y la última recarga falló.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@Readiness
@ApplicationScoped
@RequiredArgsConstructor
public class SheetsReadinessCheck implements HealthCheck {

    private final WorkbookProvider workbookProvider;

    @Override
    public HealthCheckResponse call() {
        boolean down = !workbookProvider.hasValidLoad() && workbookProvider.lastReloadFailed();
        return HealthCheckResponse.named("sheets").status(!down).build();
    }
}
