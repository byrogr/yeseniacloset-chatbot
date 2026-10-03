package pe.rmsolutions.chatbot.whatsapp.model;

import java.util.List;

/**
 * Un cambio ({@code entry[].changes[]}) del webhook, con lo que el canal necesita procesar.
 *
 * @param field         campo suscrito que originó el cambio ({@code messages}, {@code smb_message_echoes}, ...)
 * @param phoneNumberId id del número del negocio al que corresponde el cambio
 * @param messages      mensajes entrantes de clientas
 * @param echoes        mensajes enviados por la dueña desde su app
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record InboundChange(String field, String phoneNumberId, List<InboundMessage> messages,
                            List<InboundEcho> echoes) {
}
