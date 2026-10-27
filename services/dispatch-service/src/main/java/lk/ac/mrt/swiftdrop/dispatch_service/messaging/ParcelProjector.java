package lk.ac.mrt.swiftdrop.dispatch_service.messaging;

import java.time.Clock;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.events.*;
import lk.ac.mrt.swiftdrop.dispatch_service.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ParcelProjector {
    private final JdbcTemplate jdbc;
    private final ParcelRepository parcels;
    private final CourierRepository couriers;
    private final AssignmentRepository assignments;
    private final CommandRepository commands;
    private final JsonMapper mapper;
    private final Clock clock;
    public ParcelProjector(JdbcTemplate jdbc, ParcelRepository parcels, CourierRepository couriers,
            AssignmentRepository assignments, CommandRepository commands, JsonMapper mapper, Clock clock) {
        this.jdbc=jdbc; this.parcels=parcels; this.couriers=couriers; this.assignments=assignments;
        this.commands=commands; this.mapper=mapper; this.clock=clock;
    }
    @Transactional public void apply(EventEnvelope<ParcelDispatchChangedV1> envelope) {
        if (!envelope.eventType().equals(ParcelDispatchChangedV1.EVENT_TYPE) || envelope.version()!=1
                || !envelope.producer().equals("parcel-service")) throw new IllegalArgumentException("Unsupported dispatch event");
        if (jdbc.update("insert into processed_events(event_id,processed_at) values (?,?) on conflict do nothing",
                envelope.eventId(),java.sql.Timestamp.from(clock.instant())) == 0) return;
        var event = envelope.payload();
        String json = mapper.writeValueAsString(event);
        jdbc.update("""
            insert into dispatch_parcels(id,parcel_version,tracking_number,status,assignee_user_id,pickup_city,
                dropoff_city,zone_code,size,weight_kg,snapshot,created_at,status_at)
            values (?,-1,?,?,?,?,?,?,?,?,?,?,?) on conflict (id) do nothing
            """,event.parcelId(),event.trackingNumber(),event.status().name(),event.assigneeUserId(),event.pickup().city(),
                event.dropoff().city(),event.pickup().zoneCode(),event.size().name(),event.weightKg(),json,
                java.sql.Timestamp.from(event.createdAt()),java.sql.Timestamp.from(envelope.occurredAt()));
        var parcel = parcels.lock(event.parcelId()).orElseThrow();
        if (event.parcelVersion() <= parcel.getParcelVersion()) return;
        parcel.apply(event,json,envelope.occurredAt());
        // Pending commands own reconciliation until the remote result is confirmed locally.
        if (commands.findByPendingParcelId(event.parcelId()).isPresent()) return;
        assignments.findByActiveParcelId(event.parcelId()).ifPresent(a -> {
            var courier = couriers.lockAll(List.of(a.getCourierId())).getFirst();
            a.mirror(event.status(),envelope.occurredAt());
            if (!Objects.equals(event.assigneeUserId(),courier.getUserId()) && a.getClosedAt()==null)
                a.close(envelope.occurredAt(),"Parcel assignment changed upstream");
            if (a.getClosedAt()!=null) courier.release();
        });
    }
}
