package pe.rmsolutions.chatbot.sheets;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Convierte el {@link RawWorkbook} en un {@link ParsedWorkbook}, aplicando las reglas de
 * encabezado, celdas, fechas y estados definidas en la spec de la fase 1.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
public class WorkbookParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> REQUIRED_HEADERS =
            List.of("Producto", "Clienta", "Estado", "Monto total", "Fecha de pago");

    private final TabNamePolicy tabNamePolicy = new TabNamePolicy();

    public ParsedWorkbook parse(RawWorkbook raw) {
        List<String> warnings = new ArrayList<>();
        List<Customer> customers = parseCustomers(raw, warnings);
        Map<String, Customer> customersByKey = new HashMap<>();
        for (Customer customer : customers) {
            customersByKey.put(NameNormalizer.key(customer.name()), customer);
        }

        List<OrderRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<List<Object>>> entry : raw.values().entrySet()) {
            String title = entry.getKey();
            if (TabNamePolicy.CUSTOMERS_TAB.equals(title)) {
                continue;
            }
            tabNamePolicy.parse(title).ifPresent(campaignId ->
                    rows.addAll(parseCampaign(campaignId, entry.getValue(), customersByKey, warnings)));
        }

        return new ParsedWorkbook(customers, rows, warnings);
    }

    private List<Customer> parseCustomers(RawWorkbook raw, List<String> warnings) {
        List<List<Object>> rows = raw.values().get(TabNamePolicy.CUSTOMERS_TAB);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> columns = mapHeaders(rows.get(0));
        Integer nameColumn = columns.get(NameNormalizer.key("Nombre"));
        Integer phoneColumn = columns.get(NameNormalizer.key("Celular"));
        if (nameColumn == null || phoneColumn == null) {
            warnings.add(TabNamePolicy.CUSTOMERS_TAB + ": faltan columnas Nombre o Celular");
            return List.of();
        }

        List<Customer> customers = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            int sheetRow = i + 1;
            List<Object> row = rows.get(i);
            String name = cell(row, nameColumn);
            String rawPhone = cell(row, phoneColumn);
            if (name == null) {
                continue;
            }
            var phone = PhoneNormalizer.normalize(rawPhone);
            if (phone.isEmpty()) {
                warnings.add(TabNamePolicy.CUSTOMERS_TAB + " fila " + sheetRow
                        + ": celular inválido \"" + rawPhone + "\"");
                continue;
            }
            customers.add(new Customer(name, phone.get()));
        }
        return customers;
    }

    private List<OrderRow> parseCampaign(
            CampaignId campaignId,
            List<List<Object>> rows,
            Map<String, Customer> customersByKey,
            List<String> warnings) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> columns = mapHeaders(rows.get(0));
        for (String required : REQUIRED_HEADERS) {
            if (!columns.containsKey(NameNormalizer.key(required))) {
                warnings.add(campaignId.title() + ": falta la columna \"" + required + "\"");
                return List.of();
            }
        }

        int productColumn = columns.get(NameNormalizer.key("Producto"));
        Integer sizeColumn = columns.get(NameNormalizer.key("Talla"));
        int customerColumn = columns.get(NameNormalizer.key("Clienta"));
        Integer recipientColumn = columns.get(NameNormalizer.key("Para"));
        int paymentDateColumn = columns.get(NameNormalizer.key("Fecha de pago"));
        int statusColumn = columns.get(NameNormalizer.key("Estado"));
        int totalAmountColumn = columns.get(NameNormalizer.key("Monto total"));

        List<OrderRow> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            int sheetRow = i + 1;
            List<Object> row = rows.get(i);

            String product = cell(row, productColumn);
            if (product == null) {
                continue;
            }

            String rawCustomer = cell(row, customerColumn);
            Customer customer = rawCustomer == null
                    ? null
                    : customersByKey.get(NameNormalizer.key(rawCustomer));
            if (customer == null) {
                warnings.add(campaignId.title() + " fila " + sheetRow
                        + ": clienta \"" + rawCustomer + "\" no está en Clientas");
                continue;
            }

            String rawStatus = cell(row, statusColumn);
            OrderStatus status = parseStatus(rawStatus);
            if (status == null) {
                warnings.add(campaignId.title() + " fila " + sheetRow
                        + ": estado \"" + rawStatus + "\" desconocido");
                continue;
            }

            String size = sizeColumn == null ? null : cell(row, sizeColumn);
            String recipient = recipientColumn == null ? null : cell(row, recipientColumn);
            LocalDate paymentDate = parseDate(cell(row, paymentDateColumn), campaignId, sheetRow, warnings);
            BigDecimal totalAmount = parseAmount(cell(row, totalAmountColumn), campaignId, sheetRow, warnings);

            result.add(new OrderRow(campaignId, sheetRow, product, size, customer.name(), recipient,
                    paymentDate, status, totalAmount));
        }

        warnAboutMismatchedPaymentDates(campaignId, result, warnings);
        return result;
    }

    private void warnAboutMismatchedPaymentDates(
            CampaignId campaignId, List<OrderRow> rows, List<String> warnings) {
        Map<String, LocalDate> firstDateByCustomer = new HashMap<>();
        for (OrderRow row : rows) {
            if (row.status() != OrderStatus.PENDIENTE && row.status() != OrderStatus.ENTREGADO) {
                continue;
            }
            if (row.paymentDate() == null) {
                continue;
            }
            LocalDate previous = firstDateByCustomer.get(row.customer());
            if (previous == null) {
                firstDateByCustomer.put(row.customer(), row.paymentDate());
            } else if (!previous.equals(row.paymentDate())) {
                warnings.add(campaignId.title() + ": " + row.customer()
                        + " tiene fechas de pago distintas (" + DATE.format(previous) + " y "
                        + DATE.format(row.paymentDate()) + ")");
            }
        }
    }

    private OrderStatus parseStatus(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private LocalDate parseDate(String raw, CampaignId campaignId, int sheetRow, List<String> warnings) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DATE);
        } catch (DateTimeParseException e) {
            warnings.add(campaignId.title() + " fila " + sheetRow
                    + ": fecha de pago \"" + raw + "\" inválida");
            return null;
        }
    }

    private BigDecimal parseAmount(String raw, CampaignId campaignId, int sheetRow, List<String> warnings) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            warnings.add(campaignId.title() + " fila " + sheetRow
                    + ": monto total \"" + raw + "\" no numérico");
            return null;
        }
    }

    private Map<String, Integer> mapHeaders(List<Object> headers) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            Object value = headers.get(i);
            if (value == null) {
                continue;
            }
            String key = NameNormalizer.key(value.toString());
            if (!key.isEmpty()) {
                columns.put(key, i);
            }
        }
        return columns;
    }

    private String cell(List<Object> row, int index) {
        if (index >= row.size()) {
            return null;
        }
        Object value = row.get(index);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
