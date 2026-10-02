package pe.rmsolutions.chatbot.ops.web;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import pe.rmsolutions.chatbot.account.services.AccountStatusService;
import pe.rmsolutions.chatbot.account.services.CustomerNotFoundException;
import pe.rmsolutions.chatbot.api.CustomersApi;
import pe.rmsolutions.chatbot.ops.mapper.AccountStatusMapper;

/**
 * Endpoint de desarrollo que expone el estado de cuenta leído del Sheet. Existe en los perfiles
 * {@code dev} y {@code test} (para probarlo por HTTP); no llega al build de producción.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "test"})
@RequiredArgsConstructor
public class CustomerResource implements CustomersApi {

    private final AccountStatusService service;
    private final AccountStatusMapper mapper;

    @Override
    public Response getCustomerAccountStatus(String phone) {
        var status = service.getAccountStatus(phone);
        if (!status.registered()) {
            throw new CustomerNotFoundException();
        }
        return Response.ok(mapper.toDto(status)).build();
    }
}
