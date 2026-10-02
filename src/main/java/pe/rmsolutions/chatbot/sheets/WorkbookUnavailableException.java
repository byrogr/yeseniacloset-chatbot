package pe.rmsolutions.chatbot.sheets;

/**
 * Señala que no hay una lectura válida del Sheet, ni reciente ni vieja dentro del margen de tolerancia.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public class WorkbookUnavailableException extends RuntimeException {

    public WorkbookUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public WorkbookUnavailableException(String message) {
        super(message);
    }
}
