package pe.rmsolutions.chatbot.agent.utils;

import lombok.experimental.UtilityClass;

/**
 * Enmascara datos personales antes de escribirlos en logs.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@UtilityClass
public class MaskingUtils {

    private static final int VISIBLE_PREFIX = 5;
    private static final int VISIBLE_SUFFIX = 3;

    /**
     * {@code 51987654321} → {@code 51987***321}. Un valor demasiado corto se oculta por completo.
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() <= VISIBLE_PREFIX + VISIBLE_SUFFIX) {
            return "***";
        }
        return phone.substring(0, VISIBLE_PREFIX) + "***" + phone.substring(phone.length() - VISIBLE_SUFFIX);
    }
}
