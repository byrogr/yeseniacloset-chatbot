package pe.rmsolutions.chatbot.sheets;

/**
 * Fuente de datos crudos del Sheet: la pestaña {@code Clientas} y las pestañas de campaña.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public interface WorkbookSource {

    RawWorkbook read();
}
