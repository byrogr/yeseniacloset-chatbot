package pe.rmsolutions.chatbot.whatsapp.repository;

/**
 * Puerto de salida para enviar mensajes de texto a una clienta. Permite cambiar la Cloud API directa por
 * un proveedor (BSP) sin tocar el procesamiento.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public interface OutboundMessenger {

    /**
     * Envía un texto. Lanza una excepción si el envío falla definitivamente.
     *
     * @param to   celular de la clienta, con código de país
     * @param body texto a enviar, ya truncado al máximo permitido
     */
    void sendText(String to, String body);
}
