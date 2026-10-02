package pe.rmsolutions.chatbot.ops.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.sheets.repository.FailingWorkbookSource;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Con una fuente que siempre falla y sin carga previa en caché, el endpoint responde 503 con Problem.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@QuarkusTest
@TestProfile(CustomerResourceUnavailableTest.UnavailableWorkbook.class)
class CustomerResourceUnavailableTest {

    @Test
    void libroNoDisponibleDevuelve503ConProblem() {
        given().pathParam("phone", "51911111111")
                .when().get("/api/v1/customers/{phone}/account-status")
                .then()
                .statusCode(503)
                .contentType("application/problem+json")
                .body("status", equalTo(503));
    }

    public static class UnavailableWorkbook implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FailingWorkbookSource.class);
        }
    }
}
