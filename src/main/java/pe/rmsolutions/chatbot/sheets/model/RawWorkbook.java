package pe.rmsolutions.chatbot.sheets.model;

import java.util.List;
import java.util.Map;

/**
 * Celdas crudas leídas del Sheet, con clave igual al título de la pestaña.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record RawWorkbook(Map<String, List<List<Object>>> values) {
}
