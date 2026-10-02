package pe.rmsolutions.chatbot.account.services;

/**
 * Señala que no hay ninguna clienta registrada con el celular consultado.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException() {
        super("No customer is registered with that phone number.");
    }
}
