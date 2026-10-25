package lk.ac.mrt.swiftdrop.parcel_service.repository;

import java.util.*;
import lk.ac.mrt.swiftdrop.parcel_service.domain.Parcel;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {
    Optional<Parcel> findByCustomerIdAndBookingKey(UUID customerId, String bookingKey);
    Optional<Parcel> findByTrackingNumber(String trackingNumber);
    Page<Parcel> findByCustomerIdAndMerchantIdIsNull(UUID customerId, Pageable pageable);
    Page<Parcel> findByMerchantId(UUID merchantId, Pageable pageable);
    Page<Parcel> findByAssigneeId(UUID assigneeId, Pageable pageable);
}
