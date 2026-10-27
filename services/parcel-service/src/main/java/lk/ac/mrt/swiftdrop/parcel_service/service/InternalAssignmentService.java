package lk.ac.mrt.swiftdrop.parcel_service.service;

import jakarta.validation.constraints.*;
import java.time.Clock;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelException;
import lk.ac.mrt.swiftdrop.parcel_service.repository.ParcelRepository;
import lk.ac.mrt.swiftdrop.parcel_service.messaging.DispatchEventWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalAssignmentService {
    private final ParcelRepository parcels;
    private final DispatchEventWriter events;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public InternalAssignmentService(ParcelRepository parcels, DispatchEventWriter events, JdbcTemplate jdbc, Clock clock) {
        this.parcels=parcels; this.events=events; this.jdbc=jdbc; this.clock=clock;
    }
    public record Command(@NotNull UUID commandId, UUID expectedAssignee, UUID assigneeUserId,
                          @NotNull UUID requestedBy, @Size(max=1000) String reason) {}
    public record Result(UUID parcelId, UUID assigneeUserId, long parcelVersion) {}
    @Transactional public Result assign(UUID id, Command command) {
        var parcel = parcels.lock(id).orElseThrow(() -> new ParcelException(ErrorCode.RESOURCE_NOT_FOUND,"Parcel was not found"));
        var existing = jdbc.query("select * from parcel_assignment_commands where id=?",(rs,n) -> {
            if (!id.equals(rs.getObject("parcel_id",UUID.class))
                    || !Objects.equals(command.expectedAssignee(),rs.getObject("expected_assignee",UUID.class))
                    || !Objects.equals(command.assigneeUserId(),rs.getObject("assignee_user_id",UUID.class))
                    || !command.requestedBy().equals(rs.getObject("requested_by",UUID.class))
                    || !Objects.equals(command.reason(),rs.getString("reason")))
                throw new ParcelException(ErrorCode.CONFLICT,"Command ID was used with different data");
            return new Result(id,rs.getObject("assignee_user_id",UUID.class),rs.getLong("parcel_version"));
        },command.commandId());
        if (!existing.isEmpty()) return existing.getFirst();
        if (parcel.getStatus()!=ParcelStatus.CREATED && parcel.getStatus()!=ParcelStatus.FAILED)
            throw new ParcelException(ErrorCode.CONFLICT,"Assignment cannot change after pickup");
        if (!Objects.equals(parcel.getAssigneeId(),command.expectedAssignee()))
            throw new ParcelException(ErrorCode.CONFLICT,"Parcel assignment has changed");
        var now = clock.instant();
        parcel.assign(command.assigneeUserId(),now); parcels.flush();
        events.write(parcel,command.reason(),now);
        jdbc.update("insert into parcel_assignment_commands(id,parcel_id,expected_assignee,assignee_user_id,requested_by,reason,parcel_version,created_at) values (?,?,?,?,?,?,?,?)",
                command.commandId(),id,command.expectedAssignee(),command.assigneeUserId(),command.requestedBy(),
                command.reason(),parcel.getVersion(),java.sql.Timestamp.from(now));
        return new Result(id,parcel.getAssigneeId(),parcel.getVersion());
    }
}
