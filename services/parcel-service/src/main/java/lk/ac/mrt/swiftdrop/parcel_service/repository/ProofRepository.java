package lk.ac.mrt.swiftdrop.parcel_service.repository;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.parcel_service.domain.DeliveryProof;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProofRepository extends JpaRepository<DeliveryProof, UUID> {}
