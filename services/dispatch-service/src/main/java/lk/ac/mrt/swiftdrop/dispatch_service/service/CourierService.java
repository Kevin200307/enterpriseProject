package lk.ac.mrt.swiftdrop.dispatch_service.service;

import java.time.Clock;
import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.*;
import lk.ac.mrt.swiftdrop.dispatch_service.client.IdentityClient;
import lk.ac.mrt.swiftdrop.dispatch_service.domain.CourierEntity;
import lk.ac.mrt.swiftdrop.dispatch_service.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class CourierService {
    private final CourierRepository couriers;
    private final AssignmentRepository assignments;
    private final CommandRepository commands;
    private final IdentityClient identity;
    private final Clock clock;
    public CourierService(CourierRepository couriers, AssignmentRepository assignments, CommandRepository commands,
            IdentityClient identity, Clock clock) {
        this.couriers=couriers; this.assignments=assignments; this.commands=commands; this.identity=identity; this.clock=clock;
    }
    @Transactional public Courier create(CourierInput input) {
        if (couriers.existsByUserId(input.userId())) throw new DispatchException(409,"That user already has a courier profile");
        var user = identity.courier(input.userId());
        return couriers.saveAndFlush(new CourierEntity(input,user.fullName(),clock.instant())).view();
    }
    public PageView<Courier> list(String zone, VehicleType vehicle, Boolean active, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new DispatchException(400,"Page must be nonnegative; size must be 1 to 100");
        Specification<CourierEntity> filter = (root,query,cb) -> cb.conjunction();
        if (zone != null) filter = filter.and((r,q,c) -> c.equal(r.get("homeZoneCode"),zone));
        if (vehicle != null) filter = filter.and((r,q,c) -> c.equal(r.get("vehicleType"),vehicle));
        if (active != null) filter = filter.and((r,q,c) -> c.equal(r.get("active"),active));
        var result = couriers.findAll(filter,PageRequest.of(page,size,Sort.by("name","id"))).map(CourierEntity::view);
        return new PageView<>(result.getContent(),result.getTotalElements(),result.getTotalPages(),page,size);
    }
    @Transactional public Courier patch(UUID id, CourierPatch input) {
        var courier = couriers.lockAll(List.of(id)).stream().findFirst().orElseThrow(() -> new DispatchException(404,"Courier was not found"));
        if (input.userId() != null && !input.userId().equals(courier.getUserId()))
            throw new DispatchException(400,"A courier profile's identity user cannot be changed");
        if (Boolean.FALSE.equals(input.active()) && courier.getOpenLoad() > 0) {
            var tracking = new TreeSet<>(assignments.openTrackingNumbers(id)); tracking.addAll(commands.pendingTrackingNumbers(id));
            throw new DispatchException(409,"Reassign open and pending parcels before deactivating this courier",List.copyOf(tracking));
        }
        if (input.maxDailyLoad() != null && input.maxDailyLoad() < courier.getOpenLoad())
            throw new DispatchException(409,"Maximum load cannot be less than the reserved and active workload");
        courier.patch(input); couriers.flush(); return courier.view();
    }
}
