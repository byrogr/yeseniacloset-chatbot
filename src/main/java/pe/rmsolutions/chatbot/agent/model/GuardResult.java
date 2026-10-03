package pe.rmsolutions.chatbot.agent.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Resultado de la guardia de montos sobre una respuesta del modelo.
 *
 * @param status        {@link Status#OK} o {@link Status#VIOLATION}
 * @param unknownAmounts montos de la respuesta que no existen en el estado de cuenta del turno
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record GuardResult(Status status, Set<BigDecimal> unknownAmounts) {

    /**
     * Veredicto de la guardia.
     *
     * @author Roger Rojas - roger.rojas@rmsolutions.pe
     */
    public enum Status {
        OK,
        VIOLATION
    }
}
