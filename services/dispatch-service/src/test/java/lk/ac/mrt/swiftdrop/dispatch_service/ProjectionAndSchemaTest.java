package lk.ac.mrt.swiftdrop.dispatch_service;

import java.time.*;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectionAndSchemaTest extends IntegrationSupport {
    @Test void duplicateAndOutOfOrderEventsNeverRegressProjection() {
        var p=UUID.randomUUID(); var created=event(p,0,ParcelStatus.CREATED,null,Instant.now());
        projector.apply(created); projector.apply(created);
        var delivered=event(p,3,ParcelStatus.DELIVERED,null,Instant.now()); projector.apply(delivered);
        projector.apply(event(p,1,ParcelStatus.PICKED_UP,null,Instant.now().plusSeconds(1)));
        assertThat(jdbc.queryForObject("select count(*) from dispatch_parcels",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from dispatch_parcels",String.class)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("select count(*) from processed_events",Integer.class)).isEqualTo(3);
        assertThatThrownBy(() -> projector.apply(new EventEnvelope<>(UUID.randomUUID(),"wrong",1,Instant.now(),"parcel-service",created.payload())))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidProjectionRollsBackProcessedEventAtomically() {
        var first=UUID.randomUUID(); var event=event(first,0,ParcelStatus.CREATED,null,Instant.now()); projector.apply(event);
        var payload=event.payload();
        var duplicateTracking=new ParcelDispatchChangedV1(UUID.randomUUID(),payload.trackingNumber(),0,payload.status(),null,
                payload.pickup(),payload.dropoff(),payload.size(),payload.weightKg(),payload.createdAt(),null);
        UUID eventId=UUID.randomUUID();
        assertThatThrownBy(() -> projector.apply(new EventEnvelope<>(eventId,ParcelDispatchChangedV1.EVENT_TYPE,1,Instant.now(),"parcel-service",duplicateTracking)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from processed_events where event_id=?",Integer.class,eventId)).isZero();
    }
    @Test void migrationEnforcesUniqueUsersActiveAssignmentsEventIdsAndCapacity() throws Exception {
        var c=courier("A","COLOMBO",1); var p=parcel(); assign(p,c.id(),false).andExpect(status().isOk());
        assertThatThrownBy(() -> jdbc.update("update couriers set open_load=2 where id=?",c.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update couriers set max_daily_load=0 where id=?",c.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into couriers select ?,version,user_id,name,vehicle_type,home_zone_code,phone,max_daily_load,open_load,active,created_at from couriers where id=?",UUID.randomUUID(),c.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into assignments select ?,version,parcel_id,active_parcel_id,courier_id,assigned_by,assigned_at,closed_at,completed_at,failed_at,close_reason,status from assignments",UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID eventId=jdbc.queryForObject("select event_id from processed_events limit 1",UUID.class);
        assertThatThrownBy(() -> jdbc.update("insert into processed_events values (?,now())",eventId)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void failedStatusReleasesLoadAndStaleEventsDoNotReopenAssignment() throws Exception {
        var c=courier("A","COLOMBO",2); var p=parcel(); assign(p,c.id(),false).andExpect(status().isOk());
        projector.apply(event(p,4,ParcelStatus.FAILED,c.userId(),Instant.now()));
        projector.apply(event(p,3,ParcelStatus.OUT_FOR_DELIVERY,c.userId(),Instant.now().plusSeconds(1)));
        assertThat(jdbc.queryForObject("select open_load from couriers",Integer.class)).isZero();
        mvc.perform(get("/api/v1/dispatch/workload").header("Authorization",admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].failedToday").value(1));
    }
    @Test void dailyStatsUseColomboMidnightAndReturnEmptyDays() throws Exception {
        var c=courier("A","COLOMBO",3); assign(parcel(),c.id(),false).andExpect(status().isOk());
        jdbc.update("update assignments set assigned_at=?",java.sql.Timestamp.from(Instant.parse("2026-10-01T18:30:00Z")));
        mvc.perform(get("/api/v1/dispatch/stats/daily").header("Authorization",admin()).param("from","2026-10-01").param("to","2026-10-03"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].created").value(0))
                .andExpect(jsonPath("$[1].created").value(1)).andExpect(jsonPath("$[2].created").value(0));
    }
}
