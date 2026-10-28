package lk.ac.mrt.swiftdrop.dispatch_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.CommandView;

@Entity @Table(name="assignment_commands") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AssignmentCommand {
    @Id private UUID id;
    @Version private long version;
    private UUID parcelId;
    private UUID pendingParcelId;
    private UUID courierId;
    private UUID expectedAssignee;
    private long baseParcelVersion;
    private UUID targetAssignee;
    private UUID requestedBy;
    private String requestId;
    private String reason;
    private String state;
    private String detail;
    private Instant createdAt;
    public AssignmentCommand(UUID parcel, UUID courier, UUID expected, long baseParcelVersion, UUID target, UUID actor,
                             String requestId, String reason, Instant now) {
        id = UUID.randomUUID(); parcelId = parcel; pendingParcelId = parcel; courierId = courier;
        expectedAssignee = expected; targetAssignee = target; requestedBy = actor;
        this.baseParcelVersion = baseParcelVersion;
        this.requestId = requestId; this.reason = reason; state = "PENDING"; createdAt = now;
    }
    public void finish(String state, String detail) { this.state = state; this.detail = detail; pendingParcelId = null; }
    public CommandView view() { return new CommandView(id,state,parcelId,courierId,detail); }
}
