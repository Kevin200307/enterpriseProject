package lk.ac.mrt.swiftdrop.parcel_service.service;

import java.math.BigDecimal;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelDtos.BookingRequest;

public interface PricingClient {
    Quote estimate(BookingRequest request, String bearerToken);
    record Quote(BigDecimal fee, String currency) {}
}
