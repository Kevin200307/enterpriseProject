package lk.ac.mrt.swiftdrop.ai_assistant_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

class AssistantToolsTest {
    @Test
    void exposesOnlyTrackingAndPricingCallbacks() {
        AssistantTools tools = tools();
        var callbacks = tools.callbacks("caller-token", List.of("CUSTOMER"), new SseEmitter());

        assertThat(callbacks)
            .extracting(callback -> callback.getToolDefinition().name())
            .containsExactly("trackParcel", "estimateFee", "listMyParcels");
    }

    @Test
    void onlyCustomerRoleGetsOwnerScopedParcelListTool() {
        AssistantTools tools = tools();

        assertThat(tools.callbacks("caller-token", List.of("ADMIN"), new SseEmitter()))
            .extracting(callback -> callback.getToolDefinition().name())
            .containsExactly("trackParcel", "estimateFee");
        assertThat(tools.callbacks("caller-token", List.of("CUSTOMER", "ADMIN"), new SseEmitter()))
            .extracting(callback -> callback.getToolDefinition().name())
            .containsExactly("trackParcel", "estimateFee");
    }

    @Test
    void rejectsMalformedTrackingNumbersBeforeCallingParcelService() {
        AssistantTools tools = tools();
        var callback = tools.callbacks("caller-token", List.of("CUSTOMER"), new SseEmitter()).getFirst();

        assertThatThrownBy(() -> callback.call("{\"trackingNumber\":\"invalid\"}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A valid SwiftDrop tracking number is required.");
    }

    @Test
    void rejectsUnsupportedPackageSizesBeforeCallingPricingService() {
        AssistantTools tools = tools();
        var callback = tools.callbacks("caller-token", List.of("CUSTOMER"), new SseEmitter()).get(1);

        assertThatThrownBy(() -> callback.call("""
            {"fromCity":"Colombo","toCity":"Kandy","size":"HUGE","weightKg":2,"express":false}
            """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Package size must be SMALL, MEDIUM, or LARGE.");
    }

    @Test
    void relaysCallerTokenToParcelAndPricingServices() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> parcelAuthorization = new AtomicReference<>();
        AtomicReference<String> pricingAuthorization = new AtomicReference<>();
        AtomicReference<String> pricingBody = new AtomicReference<>();
        server.createContext("/api/v1/parcels/by-tracking/SD-ABCDEFGH", exchange -> {
            parcelAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, """
                {"trackingNumber":"SD-ABCDEFGH","status":"IN_TRANSIT","originCity":"Colombo","destinationCity":"Kandy","timeline":[]}
                """);
        });
        server.createContext("/api/v1/pricing/estimate", exchange -> {
            pricingAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            pricingBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, """
                {"zoneFrom":"CMB","zoneTo":"KAN","baseFee":100,"weightSurcharge":50,"expressSurcharge":0,"total":150,"currency":"LKR"}
                """);
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            AssistantTools tools = new AssistantTools(baseUrl, baseUrl, JsonMapper.builder().build());
            var callbacks = tools.callbacks("caller-token", List.of("CUSTOMER"), new SseEmitter());

            assertThat(callbacks.getFirst().call("{\"trackingNumber\":\"SD-ABCDEFGH\"}"))
                .contains("\"status\":\"IN_TRANSIT\"");
            assertThat(callbacks.get(1).call("""
                {"fromCity":"Colombo","toCity":"Kandy","size":"SMALL","weightKg":2,"express":false}
                """))
                .contains("\"total\":150");

            assertThat(parcelAuthorization).hasValue("Bearer caller-token");
            assertThat(pricingAuthorization).hasValue("Bearer caller-token");
            assertThat(pricingBody.get())
                .contains("\"fromCity\":\"Colombo\"")
                .contains("\"toCity\":\"Kandy\"")
                .contains("\"size\":\"SMALL\"")
                .contains("\"express\":false");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listMyParcelsRelaysTokenAndRemovesContactAndStreetFields() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/api/v1/parcels", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().toString());
            respond(exchange, """
                {"content":[{"trackingNumber":"SD-ABCDEFGH","status":"IN_TRANSIT","pickup":{"contactName":"Private Person","phone":"0712345678","line1":"Private Street","city":"Colombo"},"dropoff":{"contactName":"Another Person","phone":"0771234567","line1":"Hidden Road","city":"Kandy"},"size":"SMALL","weightKg":1.25,"express":false,"fee":150,"currency":"LKR","createdAt":"2026-10-28T10:00:00Z","updatedAt":"2026-10-28T11:00:00Z"}],"page":0,"size":10,"totalElements":1,"totalPages":1}
                """);
        });
        server.start();
        try {
            AssistantTools tools = new AssistantTools(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                JsonMapper.builder().build()
            );
            var callback = tools.callbacks("caller-token", List.of("CUSTOMER"), new SseEmitter()).getLast();
            String result = callback.call("{}");

            assertThat(authorization).hasValue("Bearer caller-token");
            assertThat(path).hasValue("/api/v1/parcels?page=0&size=10");
            assertThat(result)
                .contains("SD-ABCDEFGH", "IN_TRANSIT", "Colombo", "Kandy", "150")
                .doesNotContain("Private Person", "0712345678", "Private Street", "Another Person", "Hidden Road");
        } finally {
            server.stop(0);
        }
    }

    private static AssistantTools tools() {
        return new AssistantTools("http://localhost:1", "http://localhost:2", JsonMapper.builder().build());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
