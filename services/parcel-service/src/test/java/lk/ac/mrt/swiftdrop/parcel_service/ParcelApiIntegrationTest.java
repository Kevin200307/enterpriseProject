package lk.ac.mrt.swiftdrop.parcel_service;

import java.math.BigDecimal;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.domain.*;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelDtos.*;
import lk.ac.mrt.swiftdrop.parcel_service.repository.*;
import lk.ac.mrt.swiftdrop.parcel_service.service.PricingClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParcelApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @Autowired ParcelRepository parcels;
    @Autowired HistoryRepository history;
    @Autowired ProofRepository proofs;
    @Autowired OutboxRepository outbox;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @Autowired lk.ac.mrt.swiftdrop.parcel_service.service.ParcelService service;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean PricingClient pricing;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean lk.ac.mrt.swiftdrop.parcel_service.storage.ProofStorage storage;
    @Autowired lk.ac.mrt.swiftdrop.parcel_service.service.TrackingNumberGenerator tracking;
    private final Map<String, byte[]> storedProofs = new HashMap<>();
    private final UUID customer = UUID.randomUUID();
    private final UUID courier = UUID.randomUUID();
    private final UUID dispatcher = UUID.randomUUID();

    private List<lk.ac.mrt.swiftdrop.parcel_service.domain.OutboxEvent> statusEvents() {
        return outbox.findAll().stream().filter(e -> e.getRoutingKey().equals(
            lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys.PARCEL_STATUS_CHANGED_V2)).toList();
    }

    @Test void internalAssignmentIsServiceOnlyIdempotentAndPublishesVersionedSnapshot() throws Exception {
        var parcel = book("internal-assignment");
        UUID command = UUID.randomUUID();
        String body = mapper.writeValueAsString(new lk.ac.mrt.swiftdrop.parcel_service.service.InternalAssignmentService.Command(
            command,null,courier,dispatcher,"Dispatch request"));
        mvc.perform(put("/api/v1/internal/parcels/{id}/assignment",parcel.id()).with(as(dispatcher,"DISPATCHER"))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        var service = jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("client_id","dispatch-service").claim("scope","dispatch.internal"));
        var first = mvc.perform(put("/api/v1/internal/parcels/{id}/assignment",parcel.id()).with(service)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.assigneeUserId").value(courier.toString())).andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/v1/internal/parcels/{id}/assignment",parcel.id()).with(service)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(content().json(first));
        assertThat(jdbc.queryForObject("select count(*) from parcel_assignment_commands",Integer.class)).isEqualTo(1);
        var dispatchEvent = outbox.findAll().stream().filter(e -> e.getRoutingKey().equals(
            lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys.PARCEL_DISPATCH_CHANGED_V1))
            .map(e -> mapper.readTree(e.getPayload())).filter(e -> e.path("payload").path("assigneeUserId").asString().equals(courier.toString())).findFirst().orElseThrow();
        assertThat(dispatchEvent.path("payload").path("parcelVersion").asLong()).isEqualTo(1);
        String unassign = mapper.writeValueAsString(new lk.ac.mrt.swiftdrop.parcel_service.service.InternalAssignmentService.Command(
            UUID.randomUUID(),courier,null,dispatcher,"Vehicle repair"));
        mvc.perform(put("/api/v1/internal/parcels/{id}/assignment",parcel.id()).with(service)
            .contentType(MediaType.APPLICATION_JSON).content(unassign)).andExpect(status().isOk()).andExpect(jsonPath("$.assigneeUserId").isEmpty());
        mvc.perform(put("/api/v1/internal/parcels/{id}/assignment",parcel.id()).with(service)
            .contentType(MediaType.APPLICATION_JSON).content(unassign.replace("Vehicle repair","Different reason"))).andExpect(status().isConflict());
    }

    @BeforeEach void resetData() {
        storedProofs.clear();
        doAnswer(inv -> { storedProofs.put(inv.getArgument(0), inv.getArgument(1)); return null; })
            .when(storage).put(anyString(), any(byte[].class), anyString());
        when(storage.get(anyString())).thenAnswer(inv -> storedProofs.get(inv.getArgument(0)));
        when(storage.presign(anyString(),eq(300))).thenReturn("https://storage.test/proof?X-Amz-Expires=300");
        jdbc.update("delete from parcel_assignment_commands");
        history.deleteAll(); proofs.deleteAll(); outbox.deleteAll(); parcels.deleteAll();
        when(pricing.estimate(any(), any())).thenReturn(new PricingClient.Quote(new BigDecimal("450.00"), "LKR"));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(UUID id, String role) {
        return jwt().jwt(j -> j.subject(id.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
    private BookingRequest booking(String city) {
        return booking(city, false);
    }
    private BookingRequest booking(String city, boolean express) {
        return new BookingRequest(new AddressInput("Sender", "+94771234567", "1 Main St", "Colombo", "00100", "COLOMBO"),
            new AddressInput("Recipient", "+94777654321", "2 Lake Rd", city, "20000", "KANDY"),
            PackageSize.SMALL, new BigDecimal("1.250"), express);
    }
    private ParcelView book(String key) throws Exception {
        return mapper.readValue(mvc.perform(post("/api/v1/parcels").with(as(customer,"CUSTOMER"))
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(booking("Kandy"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), ParcelView.class);
    }
    private void assign(UUID id) throws Exception {
        mvc.perform(put("/api/v1/parcels/{id}/assignment", id).with(as(dispatcher,"DISPATCHER"))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new AssignmentRequest(courier))))
            .andExpect(status().isOk());
    }
    private ResultActions transition(UUID id, ParcelStatus status, UUID proof, String reason) throws Exception {
        return mvc.perform(patch("/api/v1/parcels/{id}/status", id).with(as(courier,"COURIER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new StatusRequest(status, null, proof, reason))));
    }
    @Test void bookingIsPricedIdempotentAndAtomicWithHistoryAndEvent() throws Exception {
        var first = book("booking-001");
        var replay = book("booking-001");
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(first.trackingNumber()).matches("SD-[A-Z0-9]{8}");
        assertThat(first.fee()).isEqualByComparingTo("450.00");
        assertThat(parcels.count()).isEqualTo(1);
        assertThat(history.count()).isEqualTo(1);
        assertThat(statusEvents().size()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(2);
        var envelope = mapper.readTree(statusEvents().getFirst().getPayload());
        assertThat(envelope.path("eventType").asString()).isEqualTo("parcel.status.changed");
        assertThat(envelope.path("version").asInt()).isEqualTo(2);
        assertThat(envelope.path("payload").path("customerUserId").asString()).isEqualTo(customer.toString());
        assertThat(envelope.path("payload").path("toStatus").asString()).isEqualTo("CREATED");
        verify(pricing, times(1)).estimate(any(), any());
        mvc.perform(post("/api/v1/parcels").with(as(customer,"CUSTOMER"))
            .header("Idempotency-Key", "booking-001").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(booking("Galle"))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test void ownerCancellationIsIdempotentAndWritesHistoryAndV2Outbox() throws Exception {
        var parcel = book("cancel-001");

        mvc.perform(post("/api/v1/parcels/{id}/cancel", parcel.id()).with(as(customer, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/parcels/{id}/cancel", parcel.id()).with(as(customer, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(history.findByParcelIdOrderByOccurredAtAsc(parcel.id())).hasSize(2);
        assertThat(statusEvents()).hasSize(2);
        var event = mapper.readTree(statusEvents().getLast().getPayload());
        assertThat(event.path("version").asInt()).isEqualTo(2);
        assertThat(event.path("payload").path("toStatus").asString()).isEqualTo("CANCELLED");
    }

    @Test void cancellationRejectsOtherCustomersAndParcelsAfterPickup() throws Exception {
        var parcel = book("cancel-002");
        mvc.perform(post("/api/v1/parcels/{id}/cancel", parcel.id())
                        .with(as(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isForbidden());

        assign(parcel.id());
        transition(parcel.id(), ParcelStatus.PICKED_UP, null, null).andExpect(status().isOk());
        mvc.perform(post("/api/v1/parcels/{id}/cancel", parcel.id()).with(as(customer, "CUSTOMER")))
                .andExpect(status().isConflict());
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PARCEL_LIVE_BROKER_TESTS",matches="true")
    void committedTransitionsReachTheBrokerButRollbackAndIllegalTransitionsDoNot() throws Exception {
        var admin = new org.springframework.amqp.rabbit.core.RabbitAdmin(rabbit.getConnectionFactory());
        String queue = "parcel-api-acceptance-" + UUID.randomUUID();
        String exchange = lk.ac.mrt.swiftdrop.contracts.messaging.Exchanges.PARCEL_EVENTS;
        admin.declareExchange(new org.springframework.amqp.core.TopicExchange(exchange,true,false));
        admin.declareQueue(new org.springframework.amqp.core.Queue(queue,false,false,false));
        admin.declareBinding(new org.springframework.amqp.core.Binding(queue,
            org.springframework.amqp.core.Binding.DestinationType.QUEUE,exchange,
            lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys.PARCEL_STATUS_CHANGED_V2,null));
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        String dispatchQueue = queue+"-dispatch";
        admin.declareQueue(new org.springframework.amqp.core.Queue(dispatchQueue,false,false,false));
        admin.declareBinding(new org.springframework.amqp.core.Binding(dispatchQueue,
            org.springframework.amqp.core.Binding.DestinationType.QUEUE,exchange,
            lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys.PARCEL_DISPATCH_CHANGED_V1,null));
        var publisher = new lk.ac.mrt.swiftdrop.parcel_service.messaging.OutboxPublisher(outbox,rabbit,java.time.Clock.systemUTC());
        try {
            var p = book("live-booking-001");
            transaction.executeWithoutResult(status -> publisher.publish());
            var created = rabbit.receive(queue,5000);
            assertThat(created).isNotNull();
            assertThat(mapper.readTree(created.getBody()).path("payload").path("trackingNumber").asString())
                .isEqualTo(p.trackingNumber());
            assertThat(outbox.findAll().getFirst().getPublishedAt()).isNotNull();
            assertThat(rabbit.receive(queue)).isNull();
            assign(p.id());
            transition(p.id(),ParcelStatus.PICKED_UP,null,null).andExpect(status().isOk());
            transaction.executeWithoutResult(status -> publisher.publish());
            var pickedUp = rabbit.receive(queue,5000);
            assertThat(pickedUp).isNotNull();
            assertThat(mapper.readTree(pickedUp.getBody()).path("payload").path("toStatus").asString()).isEqualTo("PICKED_UP");
            assertThat(rabbit.receive(queue)).isNull();
            transition(p.id(),ParcelStatus.DELIVERED,null,null).andExpect(status().isConflict());
            var actor = new lk.ac.mrt.swiftdrop.parcel_service.service.Actor(customer,Set.of("ROLE_CUSTOMER"),"test-token");
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                service.book(booking("Kandy"),"live-rollback-001",actor);
                throw new IllegalStateException("Simulated failure");
            })).isInstanceOf(IllegalStateException.class);
            transaction.executeWithoutResult(status -> publisher.publish());
            assertThat(statusEvents().size()).isEqualTo(2);
            assertThat(rabbit.receive(queue)).isNull();
        } finally { admin.deleteQueue(queue); admin.deleteQueue(dispatchQueue); }
    }
    @Test void generatesTenThousandUniqueTrackingNumbersInTheRequiredPattern() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String number = tracking.next();
            assertThat(number).matches("SD-[A-Z0-9]{8}");
            assertThat(generated.add(number)).isTrue();
        }
    }
    @Test void feeSnapshotSurvivesLaterPricingChanges() throws Exception {
        var p = book("booking-price");
        when(pricing.estimate(any(),any())).thenReturn(new PricingClient.Quote(new BigDecimal("999.00"),"LKR"));
        mvc.perform(get("/api/v1/parcels/{id}",p.id()).with(as(customer,"CUSTOMER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fee").value(450.00));
        assertThat(book("booking-price").fee()).isEqualByComparingTo("450.00");
        assertThat(book("booking-new-price").fee()).isEqualByComparingTo("999.00");
    }

    @Test void bookingPersistsExpressChoiceAlongsideThePricedFee() throws Exception {
        when(pricing.estimate(argThat(BookingRequest::express), any()))
            .thenReturn(new PricingClient.Quote(new BigDecimal("550.00"), "LKR"));
        var result = mvc.perform(post("/api/v1/parcels").with(as(customer,"CUSTOMER"))
                .header("Idempotency-Key", "booking-express")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(booking("Kandy", true))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.express").value(true))
            .andExpect(jsonPath("$.fee").value(550.00))
            .andReturn();
        UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asString());
        assertThat(parcels.findById(id).orElseThrow().isExpress()).isTrue();
    }

    @Test void merchantBookingsAreTenantScopedAndPublishMerchantIdentity() throws Exception {
        UUID merchant = UUID.randomUUID();
        var merchantAuth = jwt().jwt(j -> j.subject(customer.toString()).claim("merchant_id",merchant.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
        var result = mvc.perform(post("/api/v1/parcels").with(merchantAuth)
            .header("Idempotency-Key","merchant-001").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(booking("Kandy")))).andExpect(status().isCreated()).andReturn();
        var p = mapper.readValue(result.getResponse().getContentAsString(),ParcelView.class);
        assertThat(p.merchantId()).isEqualTo(merchant);
        assertThat(mapper.readTree(statusEvents().getFirst().getPayload()).path("payload").path("merchantId").asString())
            .isEqualTo(merchant.toString());
        mvc.perform(get("/api/v1/parcels").with(merchantAuth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        var otherMerchant = jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("merchant_id",UUID.randomUUID().toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
        mvc.perform(get("/api/v1/parcels/{id}",p.id()).with(otherMerchant)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/parcels/{id}",p.id()).with(as(customer,"CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/parcels").with(as(customer,"MERCHANT"))
            .header("Idempotency-Key","merchant-002").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(booking("Kandy")))).andExpect(status().isForbidden());
    }

    @Test void parcelListFiltersByStatusAndInclusiveUtcDateRange() throws Exception {
        var created = book("filter-created");
        var cancelled = book("filter-cancelled");
        mvc.perform(post("/api/v1/parcels/{id}/cancel", cancelled.id()).with(as(customer,"CUSTOMER")))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/parcels")
                .param("status", "CREATED")
                .param("from", "2000-01-01")
                .param("to", "2100-01-01")
                .with(as(customer,"CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(created.id().toString()));

        mvc.perform(get("/api/v1/parcels")
                .param("status", "CANCELLED")
                .with(as(customer,"CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(cancelled.id().toString()));

        mvc.perform(get("/api/v1/parcels")
                .param("from", "2000-01-01")
                .param("to", "2000-01-02")
                .with(as(customer,"CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void parcelListRejectsAnInvertedDateRange() throws Exception {
        mvc.perform(get("/api/v1/parcels")
                .param("from", "2026-10-10")
                .param("to", "2026-10-01")
                .with(as(customer,"CUSTOMER")))
            .andExpect(status().isBadRequest());
    }

    @Test void rollbackKeepsParcelHistoryAndOutboxAtomic() {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        var actor = new lk.ac.mrt.swiftdrop.parcel_service.service.Actor(customer, Set.of("ROLE_CUSTOMER"), "test-token");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.book(booking("Kandy"), "rollback-001", actor);
            throw new IllegalStateException("Simulated transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(parcels.count()).isZero();
        assertThat(history.count()).isZero();
        assertThat(outbox.count()).isZero();
    }
    @Test void optimisticLockRejectsConcurrentParcelChanges() throws Exception {
        var view = book("booking-lock");
        var first = parcels.findById(view.id()).orElseThrow();
        var stale = parcels.findById(view.id()).orElseThrow();
        first.assign(courier, java.time.Instant.now());
        parcels.saveAndFlush(first);
        stale.assign(UUID.randomUUID(), java.time.Instant.now());
        assertThatThrownBy(() -> parcels.saveAndFlush(stale))
            .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(parcels.findById(view.id()).orElseThrow().getAssigneeId()).isEqualTo(courier);
    }
    @Test void bearerTokensUseTheRolesClaimAndUuidSubject() throws Exception {
        var token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("valid-token")
            .header("alg", "RS256").subject(customer.toString()).claim("roles", List.of("CUSTOMER")).build();
        when(decoder.decode("valid-token")).thenReturn(token);
        mvc.perform(get("/api/v1/parcels").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk());
        when(decoder.decode("valid-token")).thenReturn(org.springframework.security.oauth2.jwt.Jwt
            .withTokenValue("valid-token").header("alg", "RS256").subject("not-a-uuid")
            .claim("roles",List.of("CUSTOMER")).build());
        mvc.perform(get("/api/v1/parcels").header("Authorization", "Bearer valid-token"))
            .andExpect(status().isForbidden());
    }
    @Test void customerIsolationAndCourierAuthorization() throws Exception {
        var p = book("booking-002");
        mvc.perform(get("/api/v1/parcels/{id}",p.id()).with(as(UUID.randomUUID(),"CUSTOMER")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/parcels").with(as(UUID.randomUUID(),"CUSTOMER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(put("/api/v1/parcels/{id}/assignment",p.id()).with(as(customer,"CUSTOMER"))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new AssignmentRequest(courier))))
            .andExpect(status().isForbidden());
        assign(p.id());
        mvc.perform(patch("/api/v1/parcels/{id}/status",p.id()).with(as(UUID.randomUUID(),"COURIER"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/parcels/{id}",p.id()).with(as(courier,"COURIER"))).andExpect(status().isOk());
    }
    @Test void completeLifecycleRequiresEvidenceAndSupportsFailedReattempt() throws Exception {
        var p = book("booking-003");
        assign(p.id());
        transition(p.id(), ParcelStatus.DELIVERED, null, null).andExpect(status().isConflict());
        for (var state : List.of(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY)) {
            transition(p.id(), state, null, null).andExpect(status().isOk());
        }
        transition(p.id(), ParcelStatus.FAILED, null, null).andExpect(status().isBadRequest());
        transition(p.id(), ParcelStatus.FAILED, null, "Recipient absent").andExpect(status().isOk());
        transition(p.id(), ParcelStatus.OUT_FOR_DELIVERY, null, null).andExpect(status().isOk());
        transition(p.id(), ParcelStatus.DELIVERED, null, null).andExpect(status().isBadRequest());
        var imageBytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", imageBytes);
        byte[] png = imageBytes.toByteArray();
        var result = mvc.perform(multipart("/api/v1/parcels/{id}/proofs",p.id())
            .file(new MockMultipartFile("file", "proof.png", "image/png", png)).with(as(courier,"COURIER")))
            .andExpect(status().isCreated()).andReturn();
        UUID proof = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asString());
        mvc.perform(get("/api/v1/parcels/{id}/proofs/{proof}/url",p.id(),proof).with(as(customer,"CUSTOMER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://storage.test/proof?X-Amz-Expires=300"))
            .andExpect(jsonPath("$.expiresAt").exists());
        mvc.perform(get("/api/v1/parcels/{id}/proofs/{proof}/url",p.id(),proof).with(as(UUID.randomUUID(),"CUSTOMER")))
            .andExpect(status().isNotFound());
        transition(p.id(), ParcelStatus.DELIVERED, proof, null).andExpect(status().isOk());
        transition(p.id(), ParcelStatus.OUT_FOR_DELIVERY, null, null).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/parcels/{id}/history",p.id()).with(as(customer,"CUSTOMER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(7));
        assertThat(statusEvents().size()).isEqualTo(7);
        mvc.perform(get("/api/v1/parcels/{id}/proofs/{proof}",p.id(),proof).with(as(customer,"CUSTOMER")))
            .andExpect(status().isOk()).andExpect(content().bytes(png));
        byte[] pdf = mvc.perform(get("/api/v1/parcels/{id}/receipt",p.id()).with(as(customer,"CUSTOMER")))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn().getResponse().getContentAsByteArray();
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document)).contains(p.trackingNumber(), "450.00 LKR");
        }
    }
    @Test void invalidRequestsAndPricingFailuresLeaveNoBookings() throws Exception {
        mvc.perform(get("/api/v1/parcels")).andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/parcels").with(as(customer,"CUSTOMER"))
            .header("Idempotency-Key","booking-004").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/parcels?size=1000").with(as(customer,"CUSTOMER"))).andExpect(status().isBadRequest());
        when(pricing.estimate(any(),any())).thenThrow(new lk.ac.mrt.swiftdrop.parcel_service.api.ParcelException(
            lk.ac.mrt.swiftdrop.contracts.error.ErrorCode.UPSTREAM_UNAVAILABLE, "Pricing unavailable"));
        mvc.perform(post("/api/v1/parcels").with(as(customer,"CUSTOMER"))
            .header("Idempotency-Key","booking-004").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(booking("Kandy")))).andExpect(status().isServiceUnavailable());
        assertThat(parcels.count()).isZero();
        assertThat(history.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test void privacySafeTrackingSnapshotCanBeReadWithoutAuthentication() throws Exception {
        var parcel = book("public-tracking-snapshot");

        mvc.perform(get("/api/v1/parcels/by-tracking/{trackingNumber}", parcel.trackingNumber()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackingNumber").value(parcel.trackingNumber()))
            .andExpect(jsonPath("$.originCity").value("Colombo"))
            .andExpect(jsonPath("$.destinationCity").value("Kandy"))
            .andExpect(jsonPath("$.timeline[0].status").value("CREATED"))
            .andExpect(jsonPath("$.customerId").doesNotExist());

        mvc.perform(get("/api/v1/parcels/by-tracking/{trackingNumber}/details", parcel.trackingNumber()))
            .andExpect(status().isUnauthorized());
    }

    @Test void proofCannotBeForgedOrReadByAnotherCustomer() throws Exception {
        var p = book("booking-005");
        assign(p.id());
        for (var state : List.of(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY)) {
            transition(p.id(), state, null, null).andExpect(status().isOk());
        }
        mvc.perform(multipart("/api/v1/parcels/{id}/proofs",p.id())
            .file(new MockMultipartFile("file","fake.jpg","image/jpeg",new byte[]{77,90,0,0,1,2,3}))
            .with(as(courier,"COURIER"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/parcels/{id}/receipt",p.id()).with(as(UUID.randomUUID(),"CUSTOMER")))
            .andExpect(status().isNotFound());
        assertThat(proofs.count()).isZero();
    }
}
