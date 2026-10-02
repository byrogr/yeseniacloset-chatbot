package pe.rmsolutions.chatbot.sheets.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class TabNamePolicyTest {

    private final TabNamePolicy policy = new TabNamePolicy();

    @ParameterizedTest
    @ValueSource(strings = {"C-11-pacifika", "C-12-Carmel", "c-3-PACIFIKA", " C-11-carmel "})
    void reconoceNombresValidos(String title) {
        assertThat(policy.isCampaign(title)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLANTILLA", "Resumen", "C11-pacifika", "C-11-otro", "Campaña 3 Pacifika"})
    void rechazaNombresInvalidos(String title) {
        assertThat(policy.isCampaign(title)).isFalse();
    }

    @Test
    void parseaNumeroYCatalogoEnMinusculas() {
        var campaign = policy.parse(" C-11-CARMEL ").orElseThrow();

        assertThat(campaign.number()).isEqualTo(11);
        assertThat(campaign.catalog()).isEqualTo("carmel");
        assertThat(campaign.title()).isEqualTo("C-11-CARMEL");
    }

    @Test
    void noReconoceNombreInvalido() {
        assertThat(policy.parse("PLANTILLA")).isEmpty();
    }
}
