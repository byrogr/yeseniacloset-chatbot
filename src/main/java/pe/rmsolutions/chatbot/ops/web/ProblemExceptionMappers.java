package pe.rmsolutions.chatbot.ops.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import pe.rmsolutions.chatbot.account.services.CustomerNotFoundException;
import pe.rmsolutions.chatbot.api.model.Problem;
import pe.rmsolutions.chatbot.sheets.repository.WorkbookUnavailableException;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Traduce las excepciones conocidas a respuestas {@code application/problem+json} (RFC 9457).
 * El {@code detail} nunca incluye datos personales como el celular.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public class ProblemExceptionMappers {

    static final String PROBLEM_JSON = "application/problem+json";

    @ServerExceptionMapper
    public Response customerNotFound(CustomerNotFoundException e) {
        return problem(Response.Status.NOT_FOUND, "Customer not found",
                "No customer is registered with that phone number.");
    }

    @ServerExceptionMapper
    public Response workbookUnavailable(WorkbookUnavailableException e) {
        return problem(Response.Status.SERVICE_UNAVAILABLE, "Service unavailable",
                "The orders workbook is temporarily unavailable.");
    }

    @ServerExceptionMapper
    public Response constraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> field(v) + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return problem(Response.Status.BAD_REQUEST, "Bad request", detail);
    }

    private static String field(ConstraintViolation<?> violation) {
        return StreamSupport.stream(violation.getPropertyPath().spliterator(), false)
                .reduce((first, second) -> second)
                .map(Path.Node::getName)
                .orElse("request");
    }

    private static Response problem(Response.Status status, String title, String detail) {
        Problem body = new Problem()
                .title(title)
                .status(status.getStatusCode())
                .detail(detail);
        return Response.status(status).type(PROBLEM_JSON).entity(body).build();
    }
}
