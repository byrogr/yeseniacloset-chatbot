package pe.rmsolutions.chatbot.whatsapp.model;

/**
 * Mensaje que la dueña envió a mano desde la app de WhatsApp Business (coexistencia).
 *
 * @param messageId id de Meta del mensaje enviado
 * @param to        celular de la clienta que recibió el mensaje
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record InboundEcho(String messageId, String to) {
}
