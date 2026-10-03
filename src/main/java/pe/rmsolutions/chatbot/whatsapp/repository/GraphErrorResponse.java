package pe.rmsolutions.chatbot.whatsapp.repository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo de error de la Graph API de Meta ({@code {"error": {...}}}).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphErrorResponse(@JsonProperty("error") GraphError error) {

    /**
     * Detalle del error. Solo se registra {@code code}: el {@code message} puede incluir datos personales.
     *
     * @author Roger Rojas - roger.rojas@rmsolutions.pe
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphError(
            @JsonProperty("code") Integer code,
            @JsonProperty("error_subcode") Integer errorSubcode,
            @JsonProperty("type") String type,
            @JsonProperty("fbtrace_id") String fbtraceId) {
    }
}
