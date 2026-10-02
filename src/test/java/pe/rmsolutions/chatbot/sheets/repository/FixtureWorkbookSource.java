package pe.rmsolutions.chatbot.sheets.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import pe.rmsolutions.chatbot.sheets.model.RawWorkbook;
import pe.rmsolutions.chatbot.sheets.utils.TabNamePolicy;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee el fixture JSON de pruebas y aplica {@link TabNamePolicy} igual que la fuente real.
 * Es un bean CDI (perfil {@code test}) para que los tests de componente reciban datos
 * del fixture en vez de llamar a la API real de Google Sheets.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
public class FixtureWorkbookSource implements WorkbookSource {

    private static final String DEFAULT_RESOURCE = "fixtures/workbook-fase1.json";

    private final String resource;
    private final TabNamePolicy tabNamePolicy = new TabNamePolicy();

    public FixtureWorkbookSource() {
        this(DEFAULT_RESOURCE);
    }

    public FixtureWorkbookSource(String resource) {
        this.resource = resource;
    }

    @Override
    public RawWorkbook read() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Fixture no encontrado: " + resource);
            }
            Map<String, List<List<Object>>> all =
                    mapper.readValue(in, new TypeReference<Map<String, List<List<Object>>>>() {});
            Map<String, List<List<Object>>> filtered = new HashMap<>();
            for (Map.Entry<String, List<List<Object>>> entry : all.entrySet()) {
                if (TabNamePolicy.CUSTOMERS_TAB.equals(entry.getKey()) || tabNamePolicy.isCampaign(entry.getKey())) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
            return new RawWorkbook(filtered);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el fixture: " + resource, e);
        }
    }
}
