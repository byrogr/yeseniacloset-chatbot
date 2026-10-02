package pe.rmsolutions.chatbot.sheets.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import pe.rmsolutions.chatbot.sheets.model.RawWorkbook;

/**
 * Fuente que siempre falla, para simular el Sheet caído. Solo se activa en los perfiles de test
 * que la habilitan como alternativa.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@Alternative
@ApplicationScoped
public class FailingWorkbookSource implements WorkbookSource {

    @Override
    public RawWorkbook read() {
        throw new IllegalStateException("Sheet no disponible (simulado)");
    }
}
