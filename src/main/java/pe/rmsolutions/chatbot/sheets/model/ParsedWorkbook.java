package pe.rmsolutions.chatbot.sheets.model;

import java.util.List;

/**
 * Resultado de parsear el {@link RawWorkbook}: clientas, filas de pedido y advertencias.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record ParsedWorkbook(List<Customer> customers, List<OrderRow> rows, List<String> warnings) {
}
