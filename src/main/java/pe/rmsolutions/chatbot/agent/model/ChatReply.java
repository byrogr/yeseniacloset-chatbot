package pe.rmsolutions.chatbot.agent.model;

import java.util.List;
import java.util.Optional;

/**
 * Resultado de un turno de conversación.
 *
 * @param text           texto a enviar por WhatsApp; vacío si no se responde (celular inválido o chat pausado)
 * @param toolsUsed      nombres de las tools ejecutadas, en orden
 * @param guardTriggered si la guardia de montos reemplazó la respuesta del modelo
 * @param paused         si la conversación quedó pausada al terminar el turno
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record ChatReply(Optional<String> text, List<String> toolsUsed, boolean guardTriggered, boolean paused) {
}
