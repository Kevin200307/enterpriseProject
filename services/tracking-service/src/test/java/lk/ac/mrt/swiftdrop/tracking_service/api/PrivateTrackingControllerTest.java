package lk.ac.mrt.swiftdrop.tracking_service.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.tracking_service.audit.TrackingAuditLogger;
import lk.ac.mrt.swiftdrop.tracking_service.client.ParcelDetails;
import lk.ac.mrt.swiftdrop.tracking_service.client.ParcelServiceClient;
import lk.ac.mrt.swiftdrop.tracking_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.tracking_service.config.TrackingProperties;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.TimelineEntry;
import lk.ac.mrt.swiftdrop.tracking_service.error.TrackingExceptionHandler;
import lk.ac.mrt.swiftdrop.tracking_service.service.PrivateTrackingService;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TrackingController.class)
@Import({SecurityConfig.class, TrackingExceptionHandler.class, PrivateTrackingService.class})
@EnableConfigurationProperties(TrackingProperties.class)
class PrivateTrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackingProjectionService projectionService;

    @MockitoBean
    private ParcelServiceClient parcelServiceClient;

    @MockitoBean
    private TrackingAuditLogger auditLogger;

    @MockitoBean
    private lk.ac.mrt.swiftdrop.tracking_service.realtime.TrackingSseService sseService;

    private final String trackingNumber = "SD-4F7K2Q9M";
    private final UUID parcelId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-15T12:00:00Z");

    private ParcelDetails sampleParcel;

    @BeforeEach
    void setUp() {
        sampleParcel = new ParcelDetails(
                parcelId,
                trackingNumber,
                customerId,
                merchantId,
                courierId,
                new ParcelDetails.AddressView("Alice Sender", "+94771234567", "123 Galle Rd", "Colombo", "00300", "Western"),
                new ParcelDetails.AddressView("Bob Receiver", "+94719876543", "456 Peradeniya Rd", "Kandy", "20000", "Central"),
                PackageSize.MEDIUM,
                new BigDecimal("2.500"),
                new BigDecimal("450.00"),
                "LKR",
                ParcelStatus.IN_TRANSIT,
                now.minusSeconds(7200),
                now,
                1L
        );

        when(parcelServiceClient.fetchParcelDetails(trackingNumber)).thenReturn(Optional.of(sampleParcel));
        when(projectionService.getProjection(trackingNumber)).thenReturn(Optional.of(
                new TrackingProjection(
                        trackingNumber,
                        ParcelStatus.IN_TRANSIT,
                        "Colombo",
                        "Kandy",
                        "Kasun",
                        "2026-10-16T12:00:00Z",
                        now,
                        List.of(new TimelineEntry(ParcelStatus.IN_TRANSIT, now, "In Transit"))
                )
        ));
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/full", trackingNumber))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanViewAnyParcel() throws Exception {
        UUID adminId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/full", trackingNumber)
                        .with(jwt().jwt(j -> j.subject(adminId.toString()).claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.pickup.contactName").value("Alice Sender"))
                .andExpect(jsonPath("$.pickup.phone").value("+94771234567"))
                .andExpect(jsonPath("$.dropoff.line1").value("456 Peradeniya Rd"));

        verify(auditLogger).logPiiAccess(eq(trackingNumber), eq(adminId), any(), eq("GET_FULL_TRACKING"));
    }

    @Test
    void dispatcherCanViewAnyParcel() throws Exception {
        UUID dispatcherId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/full", trackingNumber)
                        .with(jwt().jwt(j -> j.subject(dispatcherId.toString()).claim("roles", List.of("DISPATCHER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.courierId").value(courierId.toString()));
    }

    @Test
    void customerCanViewOwnParcel() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/full", trackingNumber)
                        .with(jwt().jwt(j -> j.subject(customerId.toString()).claim("roles", List.of("CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()));
    }

    @Test
    void customerDeniedViewingOtherCustomerParcelReturnsFlat404() throws Exception {
        UUID otherCustomer = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/full", trackingNumber)
                        .with(jwt().jwt(j -> j.subject(otherCustomer.toString()).claim("roles", List.of("CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void merchantCanViewOwnedParcel() throws Exception {
        UUID merchantUserId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/full", trackingNumber)
                        .with(jwt().jwt(j -> j.subject(merchantUserId.toString())
                                        .claim("merchant_id", merchantId.toString())
                                        .claim("roles", List.of("MERCHANT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()));
    }

    @Test
    void merchantDeniedViewingOtherMerchantParcelReturnsFlat404() throws Exception {
        UUID otherMerchantId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/full", trackingNumber)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                        .claim("merchant_id", otherMerchantId.toString())
                                        .claim("roles", List.of("MERCHANT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void courierCanViewAssignedParcel() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/full", trackingNumber)
                        .with(jwt().jwt(j -> j.subject(courierId.toString()).claim("roles", List.of("COURIER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_COURIER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(courierId.toString()));
    }

    @Test
    void courierDeniedViewingUnassignedParcelReturnsFlat404() throws Exception {
        UUID otherCourier = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/full", trackingNumber)
                        .with(jwt().jwt(j -> j.subject(otherCourier.toString()).claim("roles", List.of("COURIER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_COURIER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unknownTrackingNumberReturns404() throws Exception {
        String unknown = "SD-99999999";
        when(parcelServiceClient.fetchParcelDetails(unknown)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/full", unknown)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void malformedTrackingNumberReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/full", "bad-tn")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
