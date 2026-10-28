package lk.ac.mrt.swiftdrop.dispatch_service;

import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.dispatch_service.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssignmentRecoveryTest extends IntegrationSupport {
    @Autowired AssignmentService service;
    @Autowired AssignmentTransactions transactions;
    @Test void rejectedReassignmentDoesNotCloseAnActiveRetryOfFailedParcel() throws Exception {
        var a=courier("A","COLOMBO",3); var b=courier("B","COLOMBO",3); var p=parcel();
        assign(p,a.id(),false).andExpect(status().isOk());
        REMOTE.get(p).version=2;
        projector.apply(event(p,2,ParcelStatus.FAILED,a.userId(),Instant.now()));
        assign(p,a.id(),true).andExpect(status().isOk());
        PARCEL_STATUS.set(409); assign(p,b.id(),true).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from assignments where closed_at is null",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sum(open_load) from couriers",Integer.class)).isEqualTo(1);
    }
    @Test void terminalEventBetweenRemoteCommitAndConfirmationReleasesReservation() throws Exception {
        var c=courier("A","COLOMBO",2); var p=parcel(); LOSE_RESPONSE.set(true);
        var result=assign(p,c.id(),false).andExpect(status().isAccepted()).andReturn();
        var command=UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asString());
        projector.apply(event(p,2,ParcelStatus.DELIVERED,c.userId(),Instant.now()));
        assertThat(service.retry(command).state()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select open_load from couriers",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from assignments where closed_at is null",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from assignments where completed_at is not null",Integer.class)).isEqualTo(1);
    }
    @Test void rejectedPendingReassignmentReconcilesNewTerminalSnapshot() throws Exception {
        var old=courier("A","COLOMBO",2); var next=courier("B","COLOMBO",2); var p=parcel();
        assign(p,old.id(),false).andExpect(status().isOk());
        PARCEL_STATUS.set(503);
        var result=assign(p,next.id(),true).andExpect(status().isAccepted()).andReturn();
        var command=UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asString());
        projector.apply(event(p,2,ParcelStatus.DELIVERED,old.userId(),Instant.now()));
        PARCEL_STATUS.set(409);
        assertThatThrownBy(() -> service.retry(command)).isInstanceOf(lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchException.class);
        assertThat(jdbc.queryForObject("select sum(open_load) from couriers",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from assignments where closed_at is null",Integer.class)).isZero();
    }
    @Test void schedulerRecoversPendingCommandsAndKeepsProcessingAfterRejections() throws Exception {
        var c=courier("A","COLOMBO",3);
        PARCEL_STATUS.set(503); assign(parcel(),c.id(),false).andExpect(status().isAccepted());
        assertThat(transactions.pending()).hasSize(1);
        var retrier=new AssignmentRetrier(transactions,service);
        PARCEL_STATUS.set(409); retrier.retry(); assertThat(transactions.pending()).isEmpty();
        PARCEL_STATUS.set(503); assign(parcel(),c.id(),false).andExpect(status().isAccepted());
        PARCEL_STATUS.set(200); retrier.retry(); assertThat(transactions.pending()).isEmpty();
        assertThat(jdbc.queryForObject("select open_load from couriers",Integer.class)).isEqualTo(1);
    }
    @Test void pendingUnassignmentRetriesAndSupersededAssignmentCannotUnassignNewWork() throws Exception {
        var a=courier("A","COLOMBO",3); var b=courier("B","COLOMBO",3); var p=parcel();
        assign(p,a.id(),false).andExpect(status().isOk());
        var old=jdbc.queryForObject("select id from assignments",UUID.class);
        assign(p,b.id(),true).andExpect(status().isOk()); assign(p,a.id(),true).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/dispatch/assignments/{id}",old).header("Authorization",admin()).contentType("application/json")
                .content("{\"reason\":\"Stale browser request\"}")).andExpect(status().isConflict());
        var current=jdbc.queryForObject("select id from assignments where closed_at is null",UUID.class);
        LOSE_RESPONSE.set(true);
        var response=mvc.perform(delete("/api/v1/dispatch/assignments/{id}",current).header("Authorization",admin()).contentType("application/json")
                .content("{\"reason\":\"Vehicle repair\"}")).andExpect(status().isAccepted()).andReturn();
        var command=UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).path("id").asString());
        assertThat(service.retry(command).state()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select sum(open_load) from couriers",Integer.class)).isZero();
    }
}
