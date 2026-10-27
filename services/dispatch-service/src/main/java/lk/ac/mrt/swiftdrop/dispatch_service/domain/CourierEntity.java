package lk.ac.mrt.swiftdrop.dispatch_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.*;

@Entity @Table(name="couriers") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CourierEntity {
    @Id private UUID id;
    @Version private long version;
    private UUID userId;
    private String name;
    @Enumerated(EnumType.STRING) private VehicleType vehicleType;
    private String homeZoneCode;
    private String phone;
    private int maxDailyLoad;
    private int openLoad;
    private boolean active;
    private Instant createdAt;

    public CourierEntity(CourierInput input, String name, Instant now) {
        id = UUID.randomUUID(); userId = input.userId(); this.name = name;
        vehicleType = input.vehicleType(); homeZoneCode = input.homeZoneCode(); phone = input.phone();
        maxDailyLoad = input.maxDailyLoad(); active = input.active() == null || input.active(); createdAt = now;
    }
    public void patch(CourierPatch patch) {
        if (patch.vehicleType() != null) vehicleType = patch.vehicleType();
        if (patch.homeZoneCode() != null) homeZoneCode = patch.homeZoneCode();
        if (patch.phone() != null) phone = patch.phone();
        if (patch.maxDailyLoad() != null) maxDailyLoad = patch.maxDailyLoad();
        if (patch.active() != null) active = patch.active();
    }
    public void reserve() { openLoad++; }
    public void release() { openLoad--; }
    public Courier view() { return new Courier(id,userId,name,vehicleType,homeZoneCode,phone,maxDailyLoad,active); }
}
