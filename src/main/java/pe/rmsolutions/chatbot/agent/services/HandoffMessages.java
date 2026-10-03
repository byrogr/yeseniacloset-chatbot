package pe.rmsolutions.chatbot.agent.services;

import jakarta.enterprise.context.ApplicationScoped;
import pe.rmsolutions.chatbot.agent.config.BotConfig;

import java.util.Optional;

/**
 * Textos fijos de derivación a la dueña, sin LLM. Con {@code bot.owner-contact-phone} configurado, el número
 * del bot no lo atiende nadie a mano: los textos mandan a la clienta al chat personal de la dueña y derivar
 * no pausa la conversación. Sin él (coexistencia), la dueña responde en el mismo chat y el bot se pausa.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
public class HandoffMessages {

    static final String WA_ME = "https://wa.me/";

    private final String ownerName;
    private final Optional<String> contactLink;

    public HandoffMessages(BotConfig config) {
        this.ownerName = config.ownerName();
        this.contactLink = config.ownerContactPhone().map(phone -> WA_ME + phone);
    }

    public String ownerName() {
        return ownerName;
    }

    /**
     * Si la derivación manda a la clienta al número personal de la dueña en vez de pausar el chat.
     */
    public boolean redirectsToOwner() {
        return contactLink.isPresent();
    }

    public String handoff() {
        return contactLink
                .map(link -> "Eso lo ve directamente " + ownerName + ". Escríbele aquí: " + link)
                .orElse(ownerName + " te escribirá pronto.");
    }

    public String unavailable() {
        return contactLink
                .map(link -> "En este momento no puedo revisar tu pedido. Inténtalo más tarde o escríbele a "
                        + ownerName + ": " + link)
                .orElse("En este momento no puedo revisar tu pedido. " + ownerName + " te escribirá pronto.");
    }

    public String unregistered() {
        return contactLink
                .map(link -> "No encuentro pedidos con este número. Escríbele a " + ownerName + ": " + link)
                .orElse("Hola, " + ownerName + " te escribirá pronto para ayudarte con tu pedido.");
    }

    /**
     * Respuesta cuando el bot está apagado o el celular no está en la lista de piloto. Vacío en coexistencia:
     * ahí la dueña ve el mensaje en su app y el bot calla.
     */
    public Optional<String> redirectNotice() {
        return contactLink.map(link -> "Hola, soy el asistente automático de " + ownerName
                + ". En este momento no estoy atendiendo; escríbele a " + ownerName + ": " + link);
    }

    public String nonTextNotice() {
        return contactLink
                .map(link -> "Hola, soy el asistente automático de " + ownerName
                        + ". Solo puedo leer mensajes de texto. Escríbeme tu consulta o, si prefieres enviar audios "
                        + "o fotos, escríbele a " + ownerName + ": " + link)
                .orElse("Hola, soy el asistente automático de " + ownerName
                        + ". Por ahora solo puedo leer mensajes de texto; " + ownerName
                        + " revisará tu mensaje pronto.");
    }
}
