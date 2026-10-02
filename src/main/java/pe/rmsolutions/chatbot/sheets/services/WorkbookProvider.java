package pe.rmsolutions.chatbot.sheets.services;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.rmsolutions.chatbot.sheets.config.SheetsConfig;
import pe.rmsolutions.chatbot.sheets.model.ParsedWorkbook;
import pe.rmsolutions.chatbot.sheets.repository.WorkbookSource;
import pe.rmsolutions.chatbot.sheets.repository.WorkbookUnavailableException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cachea el {@link ParsedWorkbook}, recargándolo cuando vence el TTL y tolerando fallos
 * transitorios de la fuente mientras la última carga válida no supere {@code stale-max}.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class WorkbookProvider {

    private final WorkbookSource source;
    private final WorkbookParser parser;
    private final SheetsConfig config;
    private final Clock clock;

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<Load> lastLoad = new AtomicReference<>();
    private final AtomicBoolean lastReloadFailed = new AtomicBoolean(false);

    public ParsedWorkbook get() {
        Load current = lastLoad.get();
        if (current != null && !isExpired(current)) {
            return current.workbook();
        }

        lock.lock();
        try {
            current = lastLoad.get();
            if (current != null && !isExpired(current)) {
                return current.workbook();
            }
            return reload(current);
        } finally {
            lock.unlock();
        }
    }

    public boolean hasValidLoad() {
        return lastLoad.get() != null;
    }

    public boolean lastReloadFailed() {
        return lastReloadFailed.get();
    }

    private boolean isExpired(Load load) {
        return Duration.between(load.instant(), clock.instant()).compareTo(config.cacheTtl()) >= 0;
    }

    private boolean exceedsStaleMax(Load load) {
        return Duration.between(load.instant(), clock.instant()).compareTo(config.staleMax()) > 0;
    }

    private ParsedWorkbook reload(Load previous) {
        try {
            ParsedWorkbook parsed = parser.parse(source.read());
            for (String warning : parsed.warnings()) {
                log.warn(warning);
            }
            lastLoad.set(new Load(parsed, clock.instant()));
            lastReloadFailed.set(false);
            return parsed;
        } catch (RuntimeException e) {
            lastReloadFailed.set(true);
            if (previous != null && !exceedsStaleMax(previous)) {
                log.warn("Fallo al recargar el Sheet; se usa la última carga válida", e);
                return previous.workbook();
            }
            throw new WorkbookUnavailableException(
                    "No se pudo cargar el Sheet y no hay una carga válida reciente", e);
        }
    }

    private record Load(ParsedWorkbook workbook, Instant instant) {
    }
}
