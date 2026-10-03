package pe.rmsolutions.chatbot.agent.services;

import jakarta.enterprise.context.ApplicationScoped;
import pe.rmsolutions.chatbot.account.model.AccountStatus;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Último {@link AccountStatus} que devolvió la tool en el turno en curso, por celular. Los turnos de un
 * mismo celular son secuenciales, así que no hace falta request scope.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
public class TurnContext {

    private final Map<String, AccountStatus> statusByPhone = new ConcurrentHashMap<>();

    public void put(String phone, AccountStatus status) {
        statusByPhone.put(phone, status);
    }

    public Optional<AccountStatus> get(String phone) {
        return Optional.ofNullable(statusByPhone.get(phone));
    }

    public void clear(String phone) {
        statusByPhone.remove(phone);
    }
}
