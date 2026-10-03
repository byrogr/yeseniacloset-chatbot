package pe.rmsolutions.chatbot.whatsapp.repository;

import lombok.Getter;

/**
 * La Graph API rechazó el envío con un 4xx. No se reintenta: repetir la misma solicitud fallaría igual.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@Getter
public class GraphApiClientException extends RuntimeException {

    private final int status;
    private final Integer errorCode;

    public GraphApiClientException(int status, Integer errorCode) {
        super("Graph API respondió " + status + " (error.code=" + errorCode + ")");
        this.status = status;
        this.errorCode = errorCode;
    }
}
