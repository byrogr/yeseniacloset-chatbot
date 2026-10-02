package pe.rmsolutions.chatbot.sheets.repository;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.rmsolutions.chatbot.sheets.config.SheetsConfig;
import pe.rmsolutions.chatbot.sheets.model.RawWorkbook;
import pe.rmsolutions.chatbot.sheets.utils.TabNamePolicy;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lee las pestañas relevantes del Sheet real a través de la API de Google Sheets v4.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@UnlessBuildProfile("test")
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsWorkbookSource implements WorkbookSource {

    private static final String APPLICATION_NAME = "chatbot-pedidos";

    private final SheetsConfig config;
    private final TabNamePolicy tabNamePolicy = new TabNamePolicy();

    private Sheets sheets;

    void onStart(@Observes StartupEvent event) {
        this.sheets = buildClient();
    }

    @Override
    public RawWorkbook read() {
        try {
            List<String> titles = listRelevantTabs();
            List<String> ranges = new ArrayList<>();
            for (String title : titles) {
                if (TabNamePolicy.CUSTOMERS_TAB.equals(title)) {
                    ranges.add(title + "!A1:B");
                } else {
                    ranges.add("'" + title + "'!A1:M");
                }
            }

            BatchGetValuesResponse response = sheets.spreadsheets().values()
                    .batchGet(config.spreadsheetId())
                    .setRanges(ranges)
                    .setValueRenderOption("UNFORMATTED_VALUE")
                    .setDateTimeRenderOption("FORMATTED_STRING")
                    .execute();

            Map<String, List<List<Object>>> values = new HashMap<>();
            List<ValueRange> returnedRanges = response.getValueRanges();
            for (int i = 0; i < titles.size(); i++) {
                ValueRange range = returnedRanges.get(i);
                List<List<Object>> rows = range.getValues() == null ? List.of() : range.getValues();
                values.put(titles.get(i), rows);
            }
            return new RawWorkbook(values);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el Google Sheet", e);
        }
    }

    private List<String> listRelevantTabs() throws IOException {
        var spreadsheet = sheets.spreadsheets().get(config.spreadsheetId())
                .setFields("sheets.properties.title")
                .execute();

        List<String> titles = new ArrayList<>();
        titles.add(TabNamePolicy.CUSTOMERS_TAB);
        for (Sheet sheet : spreadsheet.getSheets()) {
            String title = sheet.getProperties().getTitle();
            if (tabNamePolicy.isCampaign(title)) {
                titles.add(title);
            }
        }
        return titles;
    }

    private Sheets buildClient() {
        try {
            GoogleCredentials credentials = loadCredentials()
                    .createScoped(List.of(SheetsScopes.SPREADSHEETS_READONLY));
            HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            return new Sheets.Builder(transport, GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo inicializar el cliente de Google Sheets", e);
        }
    }

    private GoogleCredentials loadCredentials() throws IOException {
        Optional<String> credentialsJson = nonBlankValue(config.credentialsJson());
        if (credentialsJson.isPresent()) {
            try (InputStream in = new ByteArrayInputStream(
                    credentialsJson.get().getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(in);
            }
        }

        Optional<String> credentialsFile = nonBlankValue(config.credentialsFile());
        if (credentialsFile.isPresent()) {
            try (InputStream in = new FileInputStream(credentialsFile.get())) {
                return GoogleCredentials.fromStream(in);
            }
        }

        throw new IllegalStateException(
                "Configura sheets.credentials-json (producción) o sheets.credentials-file (desarrollo)");
    }

    private Optional<String> nonBlankValue(Optional<String> value) {
        return value.filter(s -> !s.isBlank());
    }
}
