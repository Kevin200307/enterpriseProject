package lk.ac.mrt.swiftdrop.merchant_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.merchant_service.api.MerchantOrderDtos.*;
import lk.ac.mrt.swiftdrop.merchant_service.domain.*;
import lk.ac.mrt.swiftdrop.merchant_service.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MerchantOrderService {
    private final MerchantRepository merchants;
    private final MerchantOrderRepository orders;
    private final WebClient parcelClient;

    public MerchantOrderService(MerchantRepository merchants, MerchantOrderRepository orders,
                                WebClient.Builder clients,
                                @Value("${parcel-service.url:http://parcel-service:8080}") String parcelUrl) {
        this.merchants = merchants; this.orders = orders; this.parcelClient = clients.baseUrl(parcelUrl).build();
    }

    @Transactional
    public View create(UUID ownerId, Create request, String bearerToken) {
        Merchant merchant = approved(ownerId);
        String ref = request.merchantRef().trim();
        String requestHash = hash(request);
        var existing = orders.findByMerchantIdAndMerchantRef(merchant.getId(), ref);
        if (existing.isPresent()) {
            if (!MessageDigest.isEqual(existing.get().getRequestHash().getBytes(StandardCharsets.US_ASCII), requestHash.getBytes(StandardCharsets.US_ASCII)))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Merchant reference was used with a different order");
            return view(existing.get());
        }
        ParcelView parcel;
        try {
            parcel = parcelClient.post().uri("/api/v1/parcels")
                    .headers(h -> h.setBearerAuth(bearerToken))
                    .header("Idempotency-Key", idempotencyKey(merchant.getId(), ref))
                    .bodyValue(new ParcelBooking(request.pickup(), request.dropoff(), request.size(),
                            request.weightKg(), request.express()))
                    .retrieve().bodyToMono(ParcelView.class).block();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Parcel service could not create the merchant order", ex);
        }
        if (parcel == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Parcel service returned an empty response");
        return view(orders.save(MerchantOrder.builder().merchant(merchant).merchantRef(ref).parcelId(parcel.id())
                .trackingNumber(parcel.trackingNumber()).status(parcel.status()).destinationCity(request.dropoff().city())
                .fee(parcel.fee()).currency(parcel.currency()).requestHash(requestHash).build()));
    }

    @Transactional(readOnly=true)
    public Page<View> list(UUID ownerId, String query, Pageable pageable) {
        Merchant merchant = approved(ownerId);
        Page<MerchantOrder> page = query == null || query.isBlank()
                ? orders.findByMerchantId(merchant.getId(), pageable)
                : orders.findByMerchantIdAndMerchantRefContainingIgnoreCase(merchant.getId(), query.trim(), pageable);
        return page.map(MerchantOrderService::view);
    }

    @Transactional(readOnly=true)
    public View get(UUID ownerId, UUID orderId) {
        Merchant merchant = approved(ownerId);
        return orders.findByIdAndMerchantId(orderId, merchant.getId()).map(MerchantOrderService::view)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant order not found"));
    }

    private Merchant approved(UUID ownerId) {
        Merchant m = merchants.findByOwnerUserId(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
        if (m.getStatus() != MerchantStatus.APPROVED) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant is not approved");
        return m;
    }
    private static View view(MerchantOrder o) { return new View(o.getId(), o.getMerchantRef(), o.getParcelId(), o.getTrackingNumber(), o.getStatus(), o.getDestinationCity(), o.getFee(), o.getCurrency(), o.getCreatedAt()); }
    private static String idempotencyKey(UUID merchantId, String ref) { return "m_" + hash(merchantId + ":" + ref).substring(0, 32); }
    private static String hash(Object value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Objects.toString(value).getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private record ParcelBooking(Address pickup, Address dropoff, lk.ac.mrt.swiftdrop.contracts.domain.PackageSize size,
                                 java.math.BigDecimal weightKg, boolean express) {}
    private record ParcelView(UUID id, String trackingNumber, String status, java.math.BigDecimal fee, String currency, Instant createdAt) {}
}
