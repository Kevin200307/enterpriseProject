package lk.ac.mrt.swiftdrop.parcel_service.service;

import java.security.*;
import java.time.*;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.contracts.domain.TrackingNumber;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV2;
import lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelTrackingSnapshotV1;
import lk.ac.mrt.swiftdrop.parcel_service.api.*;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelDtos.*;
import lk.ac.mrt.swiftdrop.parcel_service.domain.*;
import lk.ac.mrt.swiftdrop.parcel_service.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly=true)
public class ParcelService {
    private final ParcelRepository parcels;
    private final HistoryRepository history;
    private final ProofRepository proofs;
    private final OutboxRepository outbox;
    private final PricingClient pricing;
    private final JsonMapper mapper;
    private final Clock clock;
    private final TrackingNumberGenerator trackingNumbers;
    private final lk.ac.mrt.swiftdrop.parcel_service.messaging.DispatchEventWriter dispatchEvents;

    public ParcelTrackingSnapshotV1 getSnapshotByTrackingNumber(String trackingNumber) {
        if (!TrackingNumber.isValid(trackingNumber)) {
            throw new ParcelException(ErrorCode.VALIDATION_FAILED, "Invalid tracking number format");
        }
        Parcel p = parcels.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ParcelException(ErrorCode.RESOURCE_NOT_FOUND, "Parcel was not found"));
        var historyList = history.findByParcelIdOrderByOccurredAtAsc(p.getId());
        List<ParcelTrackingSnapshotV1.SnapshotTimelineEntryV1> timeline = historyList.stream()
                .map(h -> new ParcelTrackingSnapshotV1.SnapshotTimelineEntryV1(h.getToStatus(), h.getOccurredAt(), h.getNote()))
                .toList();
        String courierFirstName = p.getAssigneeId() != null ? "Courier" : null;
        return new ParcelTrackingSnapshotV1(
                p.getId(),
                p.getTrackingNumber(),
                p.getStatus(),
                p.getPickup().getCity(),
                p.getDropoff().getCity(),
                courierFirstName,
                null,
                p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt(),
                timeline
        );
    }

    public ParcelView getDetailsByTrackingNumber(String trackingNumber) {
        if (!TrackingNumber.isValid(trackingNumber)) {
            throw new ParcelException(ErrorCode.VALIDATION_FAILED, "Invalid tracking number format");
        }
        Parcel p = parcels.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ParcelException(ErrorCode.RESOURCE_NOT_FOUND, "Parcel was not found"));
        return ParcelView.of(p);
    }

    @Transactional
    public ParcelView book(BookingRequest request, String key, Actor actor) {
        if (!actor.has("CUSTOMER") && !actor.has("MERCHANT") && !actor.has("ADMIN")) {
            throw new ParcelException(ErrorCode.ACCESS_DENIED, "Customer or merchant role is required for bookings");
        }
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,100}")) {
            throw new ParcelException(ErrorCode.VALIDATION_FAILED,
                "Idempotency-Key must contain 8–100 letters, digits, underscores or hyphens");
        }
        UUID merchantId = actor.has("MERCHANT") ? requireMerchant(actor) : null;
        String hash = hash(request);
        var existing = parcels.findByCustomerIdAndBookingKey(actor.id(), key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash) || !Objects.equals(existing.get().getMerchantId(), merchantId)) {
                throw new ParcelException(ErrorCode.CONFLICT, "Idempotency-Key was used with a different booking");
            }
            return ParcelView.of(existing.get());
        }
        var quote = pricing.estimate(request, actor.bearerToken());
        Instant now = clock.instant();
        String tracking = trackingNumbers.next();
        Parcel parcel = parcels.saveAndFlush(new Parcel(actor.id(), merchantId, tracking,
            request.pickup().toAddress(), request.dropoff().toAddress(), request.size(), request.weightKg(),
            quote.fee(), quote.currency(), key, hash, now));
        recordChange(parcel, null, actor, null, null, null, now);
        return ParcelView.of(parcel);
    }

    public ParcelView get(UUID id, Actor actor) { return ParcelView.of(readable(id, actor)); }

    public PageView<ParcelView> list(int page, int size, Actor actor) {
        var pageable = page(page, size, "createdAt");
        Page<Parcel> result;
        if (actor.manages()) result = parcels.findAll(pageable);
        else if (actor.has("COURIER")) result = parcels.findByAssigneeId(actor.id(), pageable);
        else if (actor.has("MERCHANT")) result = parcels.findByMerchantId(requireMerchant(actor), pageable);
        else if (actor.has("CUSTOMER")) result = parcels.findByCustomerIdAndMerchantIdIsNull(actor.id(), pageable);
        else throw new ParcelException(ErrorCode.ACCESS_DENIED, "Parcel access is not permitted");
        return PageView.of(result.map(ParcelView::of));
    }

    public PageView<HistoryView> history(UUID id, int page, int size, Actor actor) {
        readable(id, actor);
        return PageView.of(history.findByParcelId(id, page(page, size, "occurredAt")).map(HistoryView::of));
    }

    @Transactional
    public ParcelView assign(UUID id, UUID courierId, Actor actor) {
        if (!actor.manages()) throw new ParcelException(ErrorCode.ACCESS_DENIED, "Dispatcher role is required");
        Parcel parcel = find(id);
        if (parcel.getStatus() != ParcelStatus.CREATED && parcel.getStatus() != ParcelStatus.FAILED) {
            throw new ParcelException(ErrorCode.CONFLICT, "Couriers can be assigned before pickup or after a failed attempt");
        }
        parcel.assign(courierId, clock.instant());
        parcels.flush();
        dispatchEvents.write(parcel,"Assigned by dispatcher",clock.instant());
        return ParcelView.of(parcel);
    }

    @Transactional
    public ParcelView changeStatus(UUID id, StatusRequest request, Actor actor) {
        Parcel parcel = operable(id, actor);
        ParcelStateMachine.validate(parcel.getStatus(), request.status());
        if (parcel.getAssigneeId() == null) {
            throw new ParcelException(ErrorCode.CONFLICT, "Assign a courier before changing status");
        }
        if (request.status() == ParcelStatus.FAILED &&
            (request.failedReason() == null || request.failedReason().isBlank())) {
            throw new ParcelException(ErrorCode.VALIDATION_FAILED, "A failed delivery requires a reason");
        }
        if (request.status() != ParcelStatus.FAILED && request.failedReason() != null) {
            throw new ParcelException(ErrorCode.VALIDATION_FAILED, "Failure reason is only allowed for FAILED");
        }
        if (request.status() == ParcelStatus.DELIVERED) {
            if (request.proofId() == null) {
                throw new ParcelException(ErrorCode.VALIDATION_FAILED, "A delivered parcel requires proof of delivery");
            }
            DeliveryProof proof = proofs.findById(request.proofId()).orElseThrow(() ->
                new ParcelException(ErrorCode.RESOURCE_NOT_FOUND, "Delivery proof was not found"));
            if (!proof.getParcelId().equals(id)) {
                throw new ParcelException(ErrorCode.VALIDATION_FAILED, "Delivery proof belongs to another parcel");
            }
        } else if (request.proofId() != null) {
            throw new ParcelException(ErrorCode.VALIDATION_FAILED, "Proof is only allowed for DELIVERED");
        }
        Instant now = clock.instant();
        ParcelStatus previous = parcel.getStatus();
        parcel.changeStatus(request.status(), now);
        recordChange(parcel, previous, actor, request.note(), request.proofId(), request.failedReason(), now);
        parcels.flush();
        return ParcelView.of(parcel);
    }

    @Transactional
    public ParcelView cancel(UUID id, Actor actor) {
        Parcel parcel = find(id);
        boolean owner = actor.has("CUSTOMER") && parcel.getMerchantId() == null
                && parcel.getCustomerId().equals(actor.id());
        boolean merchantOwner = actor.has("MERCHANT") && parcel.getMerchantId() != null
                && parcel.getMerchantId().equals(requireMerchant(actor));
        if (!owner && !merchantOwner && !actor.has("ADMIN")) {
            throw new ParcelException(ErrorCode.ACCESS_DENIED, "Only the owner or an administrator can cancel this parcel");
        }
        if (parcel.getStatus() == ParcelStatus.CANCELLED) {
            return ParcelView.of(parcel);
        }
        ParcelStateMachine.validate(parcel.getStatus(), ParcelStatus.CANCELLED);
        Instant now = clock.instant();
        ParcelStatus previous = parcel.getStatus();
        parcel.changeStatus(ParcelStatus.CANCELLED, now);
        recordChange(parcel, previous, actor, "Parcel cancelled", null, null, now);
        parcels.flush();
        return ParcelView.of(parcel);
    }

    public Parcel readable(UUID id, Actor actor) {
        Parcel p = find(id);
        if (actor.manages() || actor.has("CUSTOMER") && p.getMerchantId() == null && p.getCustomerId().equals(actor.id())
            || actor.has("MERCHANT") && requireMerchant(actor).equals(p.getMerchantId())
            || actor.has("COURIER") && actor.id().equals(p.getAssigneeId())) return p;
        // Avoid revealing whether another customer's parcel exists.
        throw new ParcelException(ErrorCode.RESOURCE_NOT_FOUND, "Parcel was not found");
    }

    public Parcel operable(UUID id, Actor actor) {
        Parcel p = find(id);
        if (actor.manages() || actor.has("COURIER") && actor.id().equals(p.getAssigneeId())) return p;
        throw new ParcelException(ErrorCode.ACCESS_DENIED, "Only the assigned courier or a dispatcher can update this parcel");
    }

    private Parcel find(UUID id) {
        return parcels.findById(id).orElseThrow(() -> new ParcelException(ErrorCode.RESOURCE_NOT_FOUND, "Parcel was not found"));
    }

    private void recordChange(Parcel p, ParcelStatus from, Actor actor, String note,
                              UUID proofId, String failedReason, Instant now) {
        history.save(new StatusHistory(p.getId(), from, p.getStatus(), actor.id(), note, proofId, failedReason, now));
        var envelope = new ParcelStatusChangedV2(p.getId(), p.getTrackingNumber(), p.getCustomerId(),
            p.getMerchantId(), p.getAssigneeId(), from, p.getStatus(), actor.id(), failedReason,
            proofId).toEnvelope("parcel-service");
        outbox.save(new OutboxEvent(envelope.eventId(), mapper.writeValueAsString(envelope), now,
            RoutingKeys.PARCEL_STATUS_CHANGED_V2));
        parcels.flush();
        dispatchEvents.write(p,note,now);
    }

    private UUID requireMerchant(Actor actor) {
        if (actor.merchantId() == null) {
            throw new ParcelException(ErrorCode.ACCESS_DENIED, "Merchant bookings require a signed merchant_id claim");
        }
        return actor.merchantId();
    }

    private String hash(BookingRequest request) {
        // Normalize weight scale so 1.0 and 1.000 have the same booking identity.
        var normalized = new BookingRequest(request.pickup(), request.dropoff(), request.size(), request.weightKg().stripTrailingZeros());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(normalized)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private PageRequest page(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ParcelException(ErrorCode.VALIDATION_FAILED, "Page must be non-negative and size between 1 and 100");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort).and(Sort.by("id")));
    }

    public ParcelService(ParcelRepository parcels, HistoryRepository history, ProofRepository proofs, OutboxRepository outbox, PricingClient pricing, JsonMapper mapper, Clock clock, TrackingNumberGenerator trackingNumbers,
            lk.ac.mrt.swiftdrop.parcel_service.messaging.DispatchEventWriter dispatchEvents) {
        this.parcels = parcels;
        this.history = history;
        this.proofs = proofs;
        this.outbox = outbox;
        this.pricing = pricing;
        this.mapper = mapper;
        this.clock = clock;
        this.trackingNumbers = trackingNumbers;
        this.dispatchEvents = dispatchEvents;
    }
}
