package pe.rmsolutions.chatbot.whatsapp.repository;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo de {@code POST /{version}/{phone-number-id}/messages} para un mensaje de texto (formato de Meta).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record GraphTextMessage(
        @JsonProperty("messaging_product") String messagingProduct,
        @JsonProperty("recipient_type") String recipientType,
        @JsonProperty("to") String to,
        @JsonProperty("type") String type,
        @JsonProperty("text") Text text) {

    public static GraphTextMessage of(String to, String body) {
        return new GraphTextMessage("whatsapp", "individual", to, "text", new Text(false, body));
    }

    /**
     * Texto del mensaje, sin vista previa de enlaces.
     *
     * @author Roger Rojas - roger.rojas@rmsolutions.pe
     */
    public record Text(@JsonProperty("preview_url") boolean previewUrl, @JsonProperty("body") String body) {
    }
}
