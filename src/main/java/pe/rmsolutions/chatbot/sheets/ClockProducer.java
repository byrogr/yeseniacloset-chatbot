package pe.rmsolutions.chatbot.sheets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

/**
 * Expone el {@link Clock} del sistema para que la lógica dependiente del tiempo sea inyectable y testeable.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public class ClockProducer {

    @Produces
    @ApplicationScoped
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
