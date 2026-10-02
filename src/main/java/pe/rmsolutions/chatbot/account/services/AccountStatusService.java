package pe.rmsolutions.chatbot.account.services;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import pe.rmsolutions.chatbot.account.model.AccountStatus;
import pe.rmsolutions.chatbot.account.model.CampaignAccount;
import pe.rmsolutions.chatbot.account.model.Garment;
import pe.rmsolutions.chatbot.account.model.GarmentInfo;
import pe.rmsolutions.chatbot.account.model.RecipientGroup;
import pe.rmsolutions.chatbot.sheets.model.CampaignId;
import pe.rmsolutions.chatbot.sheets.model.Customer;
import pe.rmsolutions.chatbot.sheets.model.OrderRow;
import pe.rmsolutions.chatbot.sheets.model.OrderStatus;
import pe.rmsolutions.chatbot.sheets.model.ParsedWorkbook;
import pe.rmsolutions.chatbot.sheets.utils.NameNormalizer;
import pe.rmsolutions.chatbot.sheets.utils.PhoneNormalizer;
import pe.rmsolutions.chatbot.sheets.services.WorkbookProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calcula el {@link AccountStatus} de una clienta a partir del {@link ParsedWorkbook} vigente.
 * Ningún monto se calcula en el LLM: todo sale de aquí, con {@link BigDecimal}.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@RequiredArgsConstructor
public class AccountStatusService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final WorkbookProvider workbookProvider;

    public AccountStatus getAccountStatus(String rawPhone) {
        Optional<String> phone = PhoneNormalizer.normalize(rawPhone);
        if (phone.isEmpty()) {
            return new AccountStatus(false, null, List.of(), ZERO);
        }

        ParsedWorkbook workbook = workbookProvider.get();
        Optional<Customer> customer = workbook.customers().stream()
                .filter(c -> c.phone().equals(phone.get()))
                .findFirst();
        if (customer.isEmpty()) {
            return new AccountStatus(false, null, List.of(), ZERO);
        }

        List<OrderRow> customerRows = workbook.rows().stream()
                .filter(r -> r.customer().equals(customer.get().name()))
                .toList();

        Map<CampaignId, List<OrderRow>> rowsByCampaign = new LinkedHashMap<>();
        for (OrderRow row : customerRows) {
            rowsByCampaign.computeIfAbsent(row.campaign(), c -> new ArrayList<>()).add(row);
        }

        List<CampaignAccount> campaigns = new ArrayList<>();
        for (Map.Entry<CampaignId, List<OrderRow>> entry : rowsByCampaign.entrySet()) {
            buildCampaignAccount(entry.getKey(), entry.getValue()).ifPresent(campaigns::add);
        }
        campaigns.sort(Comparator.comparingInt(CampaignAccount::number).thenComparing(CampaignAccount::catalog));

        BigDecimal grandTotal = campaigns.stream()
                .map(CampaignAccount::total)
                .reduce(ZERO, BigDecimal::add);

        return new AccountStatus(true, customer.get().name(), campaigns, grandTotal);
    }

    private Optional<CampaignAccount> buildCampaignAccount(CampaignId campaignId, List<OrderRow> rows) {
        boolean hasChargeable = rows.stream().anyMatch(this::isChargeable);
        if (!hasChargeable) {
            return Optional.empty();
        }

        Map<String, List<OrderRow>> rowsByRecipient = new LinkedHashMap<>();
        List<GarmentInfo> soldOut = new ArrayList<>();
        List<GarmentInfo> unpriced = new ArrayList<>();

        for (OrderRow row : rows) {
            if (row.status() == OrderStatus.AGOTADO) {
                soldOut.add(new GarmentInfo(row.product(), row.size(), row.recipient()));
            } else if (isChargeable(row)) {
                if (row.totalAmount() == null) {
                    unpriced.add(new GarmentInfo(row.product(), row.size(), row.recipient()));
                } else {
                    String key = NameNormalizer.key(row.recipient());
                    rowsByRecipient.computeIfAbsent(key, c -> new ArrayList<>()).add(row);
                }
            }
        }

        List<RecipientGroup> groups = buildGroups(rowsByRecipient);
        BigDecimal total = groups.stream().map(RecipientGroup::subtotal).reduce(ZERO, BigDecimal::add);
        LocalDate paymentDate = rows.stream()
                .filter(this::isChargeable)
                .map(OrderRow::paymentDate)
                .filter(d -> d != null)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return Optional.of(new CampaignAccount(
                campaignId.title(), campaignId.number(), campaignId.catalog(),
                paymentDate, groups, soldOut, unpriced, total));
    }

    private List<RecipientGroup> buildGroups(Map<String, List<OrderRow>> rowsByRecipient) {
        List<RecipientGroup> own = new ArrayList<>();
        List<RecipientGroup> rest = new ArrayList<>();

        for (List<OrderRow> groupRows : rowsByRecipient.values()) {
            String displayedRecipient = groupRows.get(0).recipient();
            List<Garment> items = groupRows.stream()
                    .map(r -> new Garment(r.product(), r.size(), r.totalAmount(), r.status()))
                    .toList();
            BigDecimal subtotal = items.stream().map(Garment::amount).reduce(ZERO, BigDecimal::add);
            RecipientGroup group = new RecipientGroup(displayedRecipient, items, subtotal);
            if (displayedRecipient == null) {
                own.add(group);
            } else {
                rest.add(group);
            }
        }

        rest.sort(Comparator.comparing(g -> NameNormalizer.key(g.recipient())));

        List<RecipientGroup> result = new ArrayList<>(own);
        result.addAll(rest);
        return result;
    }

    private boolean isChargeable(OrderRow row) {
        return row.status() == OrderStatus.PENDIENTE || row.status() == OrderStatus.ENTREGADO;
    }
}
