package lk.ac.mrt.swiftdrop.parcel_service.service;

import java.math.BigDecimal;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.parcel_service.api.*;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelDtos.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpPricingClientTest {
    private HttpPricingClient client;
    private MockRestServiceServer server;
    private BookingRequest request;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://pricing.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpPricingClient(builder.build(), "/api/v1/pricing/estimate");
        var address = new AddressInput("Sender", "+94771234567", "1 Main St", "Colombo", "00100", "COLOMBO");
        request = new BookingRequest(address, address, PackageSize.SMALL, new BigDecimal("1.250"), true);
    }
    @Test void sendsCitiesWeightExpressAndCallerTokenAndSnapshotsTheTotal() {
        server.expect(requestTo("http://pricing.test/api/v1/pricing/estimate"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer caller-token"))
            .andExpect(content().json("{\"fromCity\":\"Colombo\",\"toCity\":\"Colombo\",\"size\":\"SMALL\",\"weightKg\":1.250,\"express\":true}"))
            .andRespond(withSuccess("{\"total\":550,\"currency\":\"LKR\"}", MediaType.APPLICATION_JSON));
        assertThat(client.estimate(request,"caller-token").fee()).isEqualByComparingTo("550.00");
        server.verify();
    }
    @Test void upstreamErrorsBecomeServiceUnavailable() {
        server.expect(requestTo("http://pricing.test/api/v1/pricing/estimate")).andRespond(withServerError());
        assertThatThrownBy(() -> client.estimate(request, null)).isInstanceOfSatisfying(ParcelException.class,
            ex -> assertThat(ex.code().httpStatus()).isEqualTo(503));
    }
    @Test void rejectsInvalidOrOverprecisePrices() {
        for (String json : new String[]{"{}", "{\"total\":-1,\"currency\":\"LKR\"}",
            "{\"total\":450.123,\"currency\":\"LKR\"}", "{\"total\":450,\"currency\":\"invalid\"}"}) {
            server.reset();
            server.expect(requestTo("http://pricing.test/api/v1/pricing/estimate"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> client.estimate(request,null)).isInstanceOf(ParcelException.class);
        }
    }
}
