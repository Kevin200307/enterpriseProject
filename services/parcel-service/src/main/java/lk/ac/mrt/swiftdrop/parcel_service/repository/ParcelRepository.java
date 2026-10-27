package lk.ac.mrt.swiftdrop.parcel_service.repository;

import java.util.*;
import lk.ac.mrt.swiftdrop.parcel_service.domain.Parcel;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Parcel p where p.id=:id")
    Optional<Parcel> lock(UUID id);
    Optional<Parcel> findByCustomerIdAndBookingKey(UUID customerId, String bookingKey);
    Page<Parcel> findByCustomerIdAndMerchantIdIsNull(UUID customerId, Pageable pageable);
    Page<Parcel> findByMerchantId(UUID merchantId, Pageable pageable);
    Page<Parcel> findByAssigneeId(UUID assigneeId, Pageable pageable);
}
