package pe.rmsolutions.chatbot.sheets;

import lombok.experimental.UtilityClass;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normaliza nombres de clientas para compararlos sin importar tildes, mayúsculas o espaciado.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@UtilityClass
public class NameNormalizer {

    public String key(String name) {
        if (name == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String collapsed = withoutAccents.replaceAll("\\s+", " ");
        String lowercase = collapsed.toLowerCase(Locale.ROOT);
        return lowercase.endsWith(".") ? lowercase.substring(0, lowercase.length() - 1) : lowercase;
    }
}
