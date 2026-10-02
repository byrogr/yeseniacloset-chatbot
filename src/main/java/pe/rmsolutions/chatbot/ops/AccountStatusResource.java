package pe.rmsolutions.chatbot.ops;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import pe.rmsolutions.chatbot.account.AccountStatusService;
import pe.rmsolutions.chatbot.ops.api.CustomersApi;
import pe.rmsolutions.chatbot.sheets.WorkbookUnavailableException;

/**
 * Endpoint de desarrollo que expone el estado de cuenta leído del Sheet. Solo existe en el
 * perfil {@code dev}; no debe llegar al build de producción.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@ApplicationScoped
@IfBuildProfile("dev")
@RequiredArgsConstructor
public class AccountStatusResource implements CustomersApi {

    private final AccountStatusService service;
    private final AccountStatusMapper mapper;

    @Override
    public Response getCustomerAccountStatus(String phone) {
        try {
            var status = service.getAccountStatus(phone);
            return Response.ok(mapper.toDto(status)).build();
        } catch (WorkbookUnavailableException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
    }
}
