package pe.rmsolutions.chatbot.sheets;

import lombok.experimental.UtilityClass;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normaliza y valida celulares peruanos al formato {@code 51} + 9 dígitos.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@UtilityClass
public class PhoneNormalizer {

    private final Pattern VALID = Pattern.compile("^51\\d{9}$");

    public Optional<String> normalize(String phone) {
        if (phone == null) {
            return Optional.empty();
        }
        String digitsOnly = phone.replaceAll("\\D", "");
        if (digitsOnly.length() == 9 && digitsOnly.startsWith("9")) {
            digitsOnly = "51" + digitsOnly;
        }
        return VALID.matcher(digitsOnly).matches() ? Optional.of(digitsOnly) : Optional.empty();
    }
}
