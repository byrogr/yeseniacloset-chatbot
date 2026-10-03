package pe.rmsolutions.chatbot.whatsapp.model;

import java.time.Instant;

/**
 * Mensaje entrante de una clienta.
 *
 * @param messageId id de Meta ({@code wamid...}), usado para deduplicar
 * @param from      celular de la clienta, tal como lo envía Meta
 * @param type      tipo de mensaje ({@code text}, {@code image}, {@code audio}, ...)
 * @param text      cuerpo del texto; nulo si no es un mensaje de texto
 * @param timestamp momento en que la clienta envió el mensaje; nulo si Meta no lo informa
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record InboundMessage(String messageId, String from, String type, String text, Instant timestamp) {
}
