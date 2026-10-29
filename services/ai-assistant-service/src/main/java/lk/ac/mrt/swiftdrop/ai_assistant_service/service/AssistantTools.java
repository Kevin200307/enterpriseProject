package lk.ac.mrt.swiftdrop.ai_assistant_service.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lk.ac.mrt.swiftdrop.ai_assistant_service.api.AssistantDtos.ToolEvent;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.contracts.domain.TrackingNumber;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AssistantTools {
    private static final String TRACKING_SCHEMA = """
        {"type":"object","properties":{"trackingNumber":{"type":"string","pattern":"^SD-[0-9A-Z]{8}$"}},"required":["trackingNumber"],"additionalProperties":false}
        """;
    private static final String ESTIMATE_SCHEMA = """
        {"type":"object","properties":{"fromCity":{"type":"string","minLength":1,"maxLength":120},"toCity":{"type":"string","minLength":1,"maxLength":120},"size":{"type":"string","enum":["SMALL","MEDIUM","LARGE"]},"weightKg":{"type":"number","exclusiveMinimum":0},"express":{"type":"boolean"}},"required":["fromCity","toCity","size","weightKg","express"],"additionalProperties":false}
        """;
    private static final String EMPTY_SCHEMA = """
        {"type":"object","properties":{},"additionalProperties":false}
        """;

    private final RestClient parcelClient;
    private final RestClient pricingClient;
    private final JsonMapper jsonMapper;

    public AssistantTools(
        @Value("${assistant.services.parcel-base-url}") String parcelBaseUrl,
        @Value("${assistant.services.pricing-base-url}") String pricingBaseUrl,
        JsonMapper jsonMapper
    ) {
        this.parcelClient = RestClient.builder().baseUrl(parcelBaseUrl).build();
        this.pricingClient = RestClient.builder().baseUrl(pricingBaseUrl).build();
        this.jsonMapper = jsonMapper;
    }

    public List<ToolCallback> callbacks(String bearerToken, List<String> roles, SseEmitter emitter) {
        List<ToolCallback> callbacks = new java.util.ArrayList<>(List.of(
            callback("trackParcel", "Look up a parcel's privacy-safe tracking status using its tracking number.",
                TRACKING_SCHEMA, input -> trackParcel(input, bearerToken), emitter),
            callback("estimateFee", "Get a live delivery fee estimate for two cities, package size, weight, and express choice.",
                ESTIMATE_SCHEMA, input -> estimateFee(input, bearerToken), emitter)
        ));
        if (isCustomerOnly(roles)) {
            callbacks.add(callback("listMyParcels",
                "List a customer's own parcels using the authenticated customer account. Does not include contact or street address details.",
                EMPTY_SCHEMA, input -> listMyParcels(input, bearerToken), emitter));
        }
        return List.copyOf(callbacks);
    }

    private static boolean isCustomerOnly(List<String> roles) {
        return roles != null && roles.size() == 1
            && Set.of("CUSTOMER", "ROLE_CUSTOMER").contains(roles.getFirst());
    }

    private ToolCallback callback(
        String name,
        String description,
        String schema,
        Function<String, String> action,
        SseEmitter emitter
    ) {
        ToolDefinition definition = ToolDefinition.builder()
            .name(name)
            .description(description)
            .inputSchema(schema)
            .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String input) {
                emitToolEvent(emitter, name, input);
                return action.apply(input);
            }
        };
    }

    private String trackParcel(String input, String bearerToken) {
        TrackingArguments arguments = readArguments(input, TrackingArguments.class);
        if (arguments.trackingNumber() == null || !TrackingNumber.isValid(arguments.trackingNumber())) {
            throw new IllegalArgumentException("A valid SwiftDrop tracking number is required.");
        }
        try {
            TrackingView tracking = parcelClient.get()
                .uri("/api/v1/parcels/by-tracking/{trackingNumber}", arguments.trackingNumber())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .body(TrackingView.class);
            return writeResult(tracking);
        } catch (RestClientResponseException error) {
            return writeResult(Map.of("error", error.getStatusCode().value() == 404
                ? "No parcel was found for that tracking number."
                : "Parcel tracking is temporarily unavailable."));
        }
    }

    private String estimateFee(String input, String bearerToken) {
        EstimateArguments arguments = readArguments(input, EstimateArguments.class);
        if (arguments.fromCity() == null || arguments.fromCity().isBlank() || arguments.fromCity().length() > 120
            || arguments.toCity() == null || arguments.toCity().isBlank() || arguments.toCity().length() > 120
            || arguments.weightKg() == null || arguments.weightKg().signum() <= 0
            || arguments.size() == null || arguments.express() == null) {
            throw new IllegalArgumentException("Valid cities, package size, positive weight, and express choice are required.");
        }

        PackageSize size;
        try {
            size = PackageSize.valueOf(arguments.size());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Package size must be SMALL, MEDIUM, or LARGE.", error);
        }

        EstimateRequest request = new EstimateRequest(
            arguments.fromCity().trim(),
            arguments.toCity().trim(),
            size,
            arguments.weightKg(),
            arguments.express()
        );
        try {
            EstimateView estimate = pricingClient.post()
                .uri("/api/v1/pricing/estimate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .body(request)
                .retrieve()
                .body(EstimateView.class);
            return writeResult(estimate);
        } catch (RestClientResponseException error) {
            return writeResult(Map.of("error", error.getStatusCode().is4xxClientError()
                ? "The pricing service could not calculate an estimate for those inputs."
                : "Pricing is temporarily unavailable."));
        }
    }

    private String listMyParcels(String input, String bearerToken) {
        readArguments(input, EmptyArguments.class);
        try {
            ParcelPage page = parcelClient.get()
                .uri("/api/v1/parcels?page=0&size=10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .body(ParcelPage.class);
            if (page == null) throw new IllegalStateException("The parcel service returned an empty list response.");
            List<ParcelSummary> summaries = page.content() == null ? List.of() : page.content().stream()
                .map(parcel -> new ParcelSummary(
                    parcel.trackingNumber(),
                    parcel.status(),
                    parcel.pickup() == null ? null : parcel.pickup().city(),
                    parcel.dropoff() == null ? null : parcel.dropoff().city(),
                    parcel.size(),
                    parcel.weightKg(),
                    parcel.express(),
                    parcel.fee(),
                    parcel.currency(),
                    parcel.createdAt(),
                    parcel.updatedAt()
                ))
                .toList();
            return writeResult(new ParcelListResult(summaries, page.totalElements(), page.totalPages()));
        } catch (RestClientResponseException error) {
            return writeResult(Map.of("error", "Your parcel list is temporarily unavailable."));
        }
    }

    private <T> T readArguments(String input, Class<T> type) {
        try {
            return jsonMapper.readValue(input, type);
        } catch (JacksonException error) {
            throw new IllegalArgumentException("The assistant supplied invalid tool arguments.", error);
        }
    }

    private String writeResult(Object result) {
        if (result == null) throw new IllegalStateException("The service returned an empty tool response.");
        try {
            return jsonMapper.writeValueAsString(result);
        } catch (JacksonException error) {
            throw new IllegalStateException("Could not encode the service tool response.", error);
        }
    }

    private void emitToolEvent(SseEmitter emitter, String name, String input) {
        Object arguments;
        try {
            arguments = jsonMapper.readValue(input, Object.class);
        } catch (JacksonException error) {
            throw new IllegalArgumentException("The assistant supplied invalid tool arguments.", error);
        }
        try {
            emitter.send(SseEmitter.event().name("tool").data(new ToolEvent(name, arguments)));
        } catch (IOException error) {
            throw new IllegalStateException("Could not send assistant tool event.", error);
        }
    }

    public record TrackingArguments(String trackingNumber) {}
    public record EmptyArguments() {}

    public record EstimateArguments(
        String fromCity,
        String toCity,
        String size,
        BigDecimal weightKg,
        Boolean express
    ) {}

    private record EstimateRequest(
        String fromCity,
        String toCity,
        PackageSize size,
        BigDecimal weightKg,
        boolean express
    ) {}

    private record TrackingView(
        String trackingNumber,
        String status,
        String originCity,
        String destinationCity,
        String courierFirstName,
        String eta,
        String updatedAt,
        List<TrackingTimelineEntry> timeline
    ) {}

    private record TrackingTimelineEntry(String status, String occurredAt, String note) {}

    private record EstimateView(
        String zoneFrom,
        String zoneTo,
        BigDecimal baseFee,
        BigDecimal weightSurcharge,
        BigDecimal expressSurcharge,
        BigDecimal total,
        String currency,
        String calculatedAt
    ) {}

    private record ParcelPage(List<ParcelResponse> content, int page, int size, long totalElements, int totalPages) {}

    private record ParcelResponse(
        String trackingNumber,
        String status,
        AddressResponse pickup,
        AddressResponse dropoff,
        String size,
        BigDecimal weightKg,
        boolean express,
        BigDecimal fee,
        String currency,
        String createdAt,
        String updatedAt
    ) {}

    private record AddressResponse(String city) {}

    private record ParcelSummary(
        String trackingNumber,
        String status,
        String originCity,
        String destinationCity,
        String size,
        BigDecimal weightKg,
        boolean express,
        BigDecimal fee,
        String currency,
        String createdAt,
        String updatedAt
    ) {}

    private record ParcelListResult(List<ParcelSummary> parcels, long totalElements, int totalPages) {}
}
