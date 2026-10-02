package pe.rmsolutions.chatbot.ops.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * Prueba {@code GET /api/v1/customers/{phone}/account-status} por HTTP contra el fixture.
 *
 * <p>El recurso lleva {@code @IfBuildProfile(anyOf = {"dev", "test"})} para que exista en el perfil
 * {@code test}; en el build de producción no se registra.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@QuarkusTest
class CustomerResourceTest {

    private static final String PATH = "/api/v1/customers/{phone}/account-status";

    @Test
    void clientaRegistradaDevuelveSuEstadoDeCuenta() {
        given().pathParam("phone", "51911111111")
                .when().get(PATH)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("customerName", equalTo("Gaby"))
                .body("grandTotal", equalTo("175.53"))
                .body("campaigns[0].campaignId", equalTo("C-11-carmel"))
                .body("campaigns[0].catalog", equalTo("CARMEL"))
                .body("campaigns[0].paymentDueDate", equalTo("2026-10-20"))
                .body("campaigns[0].total", equalTo("79.80"))
                .body("campaigns.find { it.campaignId == 'C-11-pacifika' }"
                        + ".groups.find { it.recipient == 'Ana' }.subtotal", equalTo("75.83"))
                .body("campaigns.find { it.campaignId == 'C-11-pacifika' }.groups[0].items[0].status",
                        equalTo("PENDING"))
                .body("campaigns.find { it.campaignId == 'C-11-pacifika' }.soldOutItems[0].product",
                        equalTo("Vestido azul"))
                .body("$", not(org.hamcrest.Matchers.hasKey("registered")));
    }

    @Test
    void clientaNoRegistradaDevuelve404ConProblem() {
        given().pathParam("phone", "51999999999")
                .when().get(PATH)
                .then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("status", equalTo(404))
                .body("title", equalTo("Customer not found"))
                .body("type", equalTo("about:blank"))
                .body("detail", not(containsString("51999999999")));
    }

    @Test
    void celularInvalidoDevuelve400ConProblem() {
        given().pathParam("phone", "12345")
                .when().get(PATH)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("detail", containsString("phone"))
                .body("detail", not(containsString("12345")));
    }

    @Test
    void rutasMalFormadasDevuelven404() {
        given().when().get("/customers/51911111111/account-status").then().statusCode(404);
        given().when().get("/api/v1/api/v1/customers/51911111111/account-status").then().statusCode(404);
    }
}
