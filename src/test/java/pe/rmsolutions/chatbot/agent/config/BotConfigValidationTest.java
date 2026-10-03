package pe.rmsolutions.chatbot.agent.config;

import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.validator.BeanValidationConfigValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validación de {@code bot.owner-contact-phone} al cargar la configuración (lo mismo que hace Quarkus al arrancar).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class BotConfigValidationTest {

    private static final Validator VALIDATOR = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory().getValidator();

    @Test
    void numeroDeContactoValidoSeAcepta() {
        assertThat(load("51911222333").ownerContactPhone()).contains("51911222333");
    }

    @Test
    void sinNumeroDeContactoEsModoCoexistencia() {
        assertThat(load(null).ownerContactPhone()).isEmpty();
    }

    @Test
    void numeroDeContactoSinPrefijoFallaConMensajeClaro() {
        assertThatThrownBy(() -> load("911222333"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("bot.owner-contact-phone")
                .hasMessageContaining("51 + 9 dígitos");
    }

    private static BotConfig load(String ownerContactPhone) {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder()
                .withMapping(BotConfig.class)
                .withValidator((BeanValidationConfigValidator) () -> VALIDATOR)
                .withDefaultValue("bot.owner-name", "Yesenia")
                // Sin Quarkus no hay conversor de "30m"; se pasan en ISO-8601.
                .withDefaultValue("bot.memory.ttl", "PT30M")
                .withDefaultValue("bot.pause.duration", "PT12H");
        if (ownerContactPhone != null) {
            builder.withDefaultValue("bot.owner-contact-phone", ownerContactPhone);
        }
        SmallRyeConfig config = builder.build();
        return config.getConfigMapping(BotConfig.class);
    }
}
