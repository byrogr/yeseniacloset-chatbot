package pe.rmsolutions.chatbot.whatsapp.repository;

import lombok.Getter;

/**
 * La Graph API falló con un 5xx. Es transitorio y se reintenta una vez.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@Getter
public class GraphApiServerException extends RuntimeException {

    private final int status;

    public GraphApiServerException(int status) {
        super("Graph API respondió " + status);
        this.status = status;
    }
}
