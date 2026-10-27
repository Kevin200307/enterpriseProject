package lk.ac.mrt.swiftdrop.dispatch_service.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.*;
import lk.ac.mrt.swiftdrop.dispatch_service.client.ParcelClient;
import lk.ac.mrt.swiftdrop.dispatch_service.domain.*;
import lk.ac.mrt.swiftdrop.dispatch_service.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All writers lock parcel first, then courier UUIDs in sorted order. No network calls inside these transactions. */
@Service @Transactional
public class AssignmentTransactions {
    private final ParcelRepository parcels;
    private final CourierRepository couriers;
    private final AssignmentRepository assignments;
    private final CommandRepository commands;
    private final Clock clock;
    private final EntityManager em;
    public AssignmentTransactions(ParcelRepository parcels, CourierRepository couriers, AssignmentRepository assignments,
            CommandRepository commands, Clock clock, EntityManager em) {
        this.parcels=parcels; this.couriers=couriers; this.assignments=assignments; this.commands=commands; this.clock=clock; this.em=em;
    }
    public AssignmentCommand prepare(UUID parcelId, UUID courierId, boolean reassign, UUID actor, String requestId) {
        var parcel = lock(parcelId);
        var pending = commands.findByPendingParcelId(parcelId);
        if (pending.isPresent()) {
            if (Objects.equals(pending.get().getCourierId(),courierId)) return pending.get();
            throw new DispatchException(409,"An assignment change is already pending for this parcel");
        }
        var current = assignments.findByActiveParcelId(parcelId);
        if (current.isPresent() && current.get().getCourierId().equals(courierId)) return null;
        if (!parcel.assignable()) throw new DispatchException(409,"Assign only before pickup or after a failed delivery");
        if (parcel.getAssigneeUserId() != null && !reassign) throw new DispatchException(409,"Parcel already has a courier; use reassign=true");
        var courier = couriers.lockAll(List.of(courierId)).stream().findFirst()
                .orElseThrow(() -> new DispatchException(404,"Courier was not found"));
        if (!courier.isActive()) throw new DispatchException(409,"Courier is inactive");
        if (courier.getOpenLoad() >= courier.getMaxDailyLoad()) throw new DispatchException(409,"Courier is at maximum capacity");
        courier.reserve();
        return commands.saveAndFlush(new AssignmentCommand(parcelId,courierId,parcel.getAssigneeUserId(),
                parcel.getParcelVersion(),courier.getUserId(),actor,requestId,"Assigned by dispatcher",clock.instant()));
    }
    public AssignmentCommand prepareUnassign(UUID assignmentId, UUID actor, String requestId, String reason) {
        var assignment = assignments.findById(assignmentId).orElseThrow(() -> new DispatchException(404,"Assignment was not found"));
        var parcel = lock(assignment.getParcelId());
        em.refresh(assignment);
        var pending = commands.findByPendingParcelId(parcel.getId());
        if (pending.isPresent()) {
            if (pending.get().getCourierId() == null) return pending.get();
            throw new DispatchException(409,"An assignment change is already pending for this parcel");
        }
        if (parcel.getAssigneeUserId() == null) return null;
        assignments.findByActiveParcelId(parcel.getId()).ifPresent(active -> {
            if (!active.getId().equals(assignmentId)) throw new DispatchException(409,"Assignment has been superseded");
        });
        var owner = couriers.findById(assignment.getCourierId()).orElseThrow();
        if (!owner.getUserId().equals(parcel.getAssigneeUserId())) throw new DispatchException(409,"Assignment has been superseded");
        if (!parcel.assignable()) throw new DispatchException(409,"Cannot unassign a parcel after pickup");
        return commands.saveAndFlush(new AssignmentCommand(parcel.getId(),null,parcel.getAssigneeUserId(),parcel.getParcelVersion(),null,
                actor,requestId,reason,clock.instant()));
    }
    public AssignmentCommand confirm(UUID commandId, ParcelClient.Result result) {
        var command = get(commandId);
        var parcel = lock(command.getParcelId());
        em.refresh(command);
        if (!command.getState().equals("PENDING")) return command;
        var old = assignments.findByActiveParcelId(parcel.getId());
        Set<UUID> ids = new HashSet<>();
        old.ifPresent(a -> ids.add(a.getCourierId()));
        if (command.getCourierId() != null) ids.add(command.getCourierId());
        Map<UUID,CourierEntity> locked = new HashMap<>();
        if (!ids.isEmpty()) couriers.lockAll(ids).forEach(c -> locked.put(c.getId(),c));
        old.ifPresent(a -> { a.close(clock.instant(),command.getReason()); locked.get(a.getCourierId()).release(); });
        assignments.flush();
        parcel.assigned(result.assigneeUserId(),result.parcelVersion());
        if (command.getCourierId() != null) {
            var assignment = new AssignmentEntity(parcel.getId(),command.getCourierId(),command.getRequestedBy(),
                    command.getCreatedAt(),parcel.getStatus());
            // A newer terminal event may arrive while the HTTP command is in flight.
            if (parcel.terminal() && parcel.getParcelVersion() > result.parcelVersion()) {
                assignment.mirror(parcel.getStatus(),parcel.getStatusAt());
                locked.get(command.getCourierId()).release();
            } else if (!Objects.equals(parcel.getAssigneeUserId(),command.getTargetAssignee())) {
                assignment.close(clock.instant(),"Superseded upstream before confirmation");
                locked.get(command.getCourierId()).release();
            }
            assignments.save(assignment);
        }
        command.finish("CONFIRMED",null); commands.flush(); return command;
    }
    public AssignmentCommand reject(UUID id, String detail) {
        var command = get(id); var parcel = lock(command.getParcelId()); em.refresh(command);
        if (command.getState().equals("PENDING")) {
            var old = assignments.findByActiveParcelId(parcel.getId());
            Set<UUID> ids = new HashSet<>();
            if (command.getCourierId()!=null) ids.add(command.getCourierId());
            old.ifPresent(a -> ids.add(a.getCourierId()));
            Map<UUID,CourierEntity> locked = new HashMap<>();
            if (!ids.isEmpty()) couriers.lockAll(ids).forEach(c -> locked.put(c.getId(),c));
            if (command.getCourierId() != null) locked.get(command.getCourierId()).release();
            old.ifPresent(a -> {
                if (parcel.getParcelVersion() > command.getBaseParcelVersion()) a.mirror(parcel.getStatus(),parcel.getStatusAt());
                var courier = locked.get(a.getCourierId());
                if (!Objects.equals(courier.getUserId(),parcel.getAssigneeUserId()) && a.getClosedAt()==null)
                    a.close(parcel.getStatusAt(),"Parcel assignment changed upstream");
                if (a.getClosedAt()!=null) courier.release();
            });
            command.finish("REJECTED",detail); commands.flush();
        }
        return command;
    }
    @Transactional(readOnly=true) public AssignmentCommand get(UUID id) {
        return commands.findById(id).orElseThrow(() -> new DispatchException(404,"Assignment command was not found"));
    }
    @Transactional(readOnly=true) public List<UUID> pending() {
        return commands.findByStateOrderByCreatedAtAsc("PENDING",PageRequest.of(0,20)).stream().map(AssignmentCommand::getId).toList();
    }
    private DispatchParcel lock(UUID id) {
        return parcels.lock(id).orElseThrow(() -> new DispatchException(404,"Parcel is not in the dispatch projection yet"));
    }
}
