package pe.rmsolutions.chatbot.agent.services;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pausas del asistente por celular (derivación a la dueña y, desde la Fase 3, ecos de su app).
 * Una pausa vencida equivale a no pausado.
 *
 * <p>Se guarda solo en memoria: si el contenedor se reinicia, las pausas se pierden. Es aceptable
 * en el MVP.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@RequiredArgsConstructor
public class ConversationPauseRegistry {

    private final Clock clock;
    private final Map<String, Instant> pausedUntil = new ConcurrentHashMap<>();

    public void pause(String phone, Duration duration) {
        pausedUntil.put(phone, clock.instant().plus(duration));
    }

    public boolean isPaused(String phone) {
        return pausedUntil(phone).isPresent();
    }

    public Optional<Instant> pausedUntil(String phone) {
        Instant until = pausedUntil.get(phone);
        if (until == null) {
            return Optional.empty();
        }
        if (!until.isAfter(clock.instant())) {
            pausedUntil.remove(phone, until);
            return Optional.empty();
        }
        return Optional.of(until);
    }

    public void resume(String phone) {
        pausedUntil.remove(phone);
    }
}
