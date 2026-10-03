package pe.rmsolutions.chatbot.agent.services;

/**
 * Señala que la conversación consultada no tiene una pausa vigente.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public class ConversationNotPausedException extends RuntimeException {

    public ConversationNotPausedException() {
        super("The conversation has no active pause.");
    }
}
