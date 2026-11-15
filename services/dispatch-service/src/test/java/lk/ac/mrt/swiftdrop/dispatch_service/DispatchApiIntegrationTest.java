package lk.ac.mrt.swiftdrop.dispatch_service;

import java.time.*;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.*;
import lk.ac.mrt.swiftdrop.dispatch_service.service.AssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DispatchApiIntegrationTest extends IntegrationSupport {
    @Autowired AssignmentService service;
    @Test void courierCreateListFilterPatchAndDuplicate() throws Exception {
        var c=courier("Nimal","COLOMBO",3); courier("Amal","KANDY",4);
        mvc.perform(get("/api/v1/couriers").header("Authorization",admin()).param("zone","COLOMBO")
                .param("vehicle","BIKE").param("active","true").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.content[0].name").value("Nimal"));
        mvc.perform(post("/api/v1/couriers").header("Authorization",admin()).contentType("application/json")
                .content(mapper.writeValueAsString(new CourierInput(c.userId(),VehicleType.VAN,"KANDY","0771234567",5,true))))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/couriers/{id}",c.id()).header("Authorization",admin()).contentType("application/json")
                .content("{\"vehicleType\":\"VAN\",\"homeZoneCode\":\"KANDY\",\"phone\":\"0771234567\",\"maxDailyLoad\":5,\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.vehicleType").value("VAN"));
        mvc.perform(get("/api/v1/couriers").header("Authorization",admin()).param("active","false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void validatesCourierFieldsAndImmutableIdentity() throws Exception {
        var c=courier("Nimal","COLOMBO",2);
        for (String body : List.of("{\"maxDailyLoad\":0}","{\"phone\":\"+12345\"}","{\"homeZoneCode\":\"bad zone\"}","{\"vehicleType\":\"CAR\"}"))
            mvc.perform(patch("/api/v1/couriers/{id}",c.id()).header("Authorization",admin()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(patch("/api/v1/couriers/{id}",c.id()).header("Authorization",admin()).contentType("application/json")
                .content("{\"userId\":\""+UUID.randomUUID()+"\"}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/couriers").header("Authorization",admin()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations").isArray());
        for (String query : List.of("page=-1","size=0","size=101","vehicle=CAR","active=invalid"))
            mvc.perform(get("/api/v1/couriers?"+query).header("Authorization",admin())).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/couriers/{id}",UUID.randomUUID()).header("Authorization",admin()).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }
    @Test void identityLookupRejectsMissingWrongRoleSuspendedAndUnavailableUsers() throws Exception {
        UUID id=UUID.randomUUID();
        var input=new CourierInput(id,VehicleType.BIKE,"COLOMBO","0771234567",5,true);
        var body=mapper.writeValueAsString(input);
        mvc.perform(post("/api/v1/couriers").header("Authorization",admin()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        USERS.put(id,Map.of("id",id,"fullName","Customer","roles",Set.of("CUSTOMER"),"status","ACTIVE"));
        mvc.perform(post("/api/v1/couriers").header("Authorization",admin()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        USERS.put(id,Map.of("id",id,"fullName","Courier","roles",Set.of("COURIER"),"status","SUSPENDED"));
        mvc.perform(post("/api/v1/couriers").header("Authorization",admin()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        IDENTITY_STATUS.set(503);
        mvc.perform(post("/api/v1/couriers").header("Authorization",admin()).contentType("application/json").content(body)).andExpect(status().isServiceUnavailable());
    }
    @Test void fullAssignmentReassignmentUnassignmentCycleMatchesFrontendContracts() throws Exception {
        var c=courier("Nimal","COLOMBO",2); var other=courier("Amal","COLOMBO",2); var p=parcel();
        mvc.perform(get("/api/v1/dispatch/queue").header("Authorization",admin()).param("zone","COLOMBO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].parcelId").value(p.toString()))
                .andExpect(jsonPath("$[0].pickupCity").value("Colombo")).andExpect(jsonPath("$[0].weightKg").value(1.25));
        mvc.perform(get("/api/v1/dispatch/queue").header("Authorization",admin()).param("zone","KANDY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assign(p,c.id(),false).andExpect(status().isOk());
        assertThat(REMOTE.get(p).assignee).isEqualTo(c.userId()); assertThat(REQUEST_ID.get()).isEqualTo("dispatch-api-test");
        assign(p,c.id(),false).andExpect(status().isOk()); assertThat(CALLS).hasValue(1);
        assign(p,other.id(),false).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/dispatch/queue").header("Authorization",admin())).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/couriers/me/assignments").header("Authorization","Bearer "+token(c.userId(),"COURIER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].dropoff.phone").value("********4321"));
        assign(p,other.id(),true).andExpect(status().isOk());
        var today=LocalDate.now(ZoneId.of("Asia/Colombo"));
        var response=mvc.perform(get("/api/v1/couriers/{id}/manifest",other.id()).param("date",today.toString()).header("Authorization",admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].parcelId").value(p.toString())).andReturn();
        UUID assignmentId=UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).get(0).path("id").asString());
        mvc.perform(delete("/api/v1/dispatch/assignments/{id}",assignmentId).header("Authorization",admin())
                .contentType("application/json").content("{\"reason\":\"Courier vehicle repair\"}"))
                .andExpect(status().isOk());
        assertThat(REMOTE.get(p).assignee).isNull();
        assertThat(jdbc.queryForObject("select count(*) from assignments",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from assignments where closed_at is null",Integer.class)).isZero();
        mvc.perform(get("/api/v1/dispatch/queue").header("Authorization",admin())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/v1/dispatch/assignments/{id}",assignmentId).header("Authorization",admin())
                .contentType("application/json").content("{\"reason\":\"Already removed\"}")).andExpect(status().isOk());
    }
    @Test void inactiveFullCouriersAndDeactivationRules() throws Exception {
        var c=courier("Nimal","COLOMBO",1); var p=parcel(); assign(p,c.id(),false).andExpect(status().isOk());
        assign(parcel(),c.id(),false).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/couriers/{id}",c.id()).header("Authorization",admin()).contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.trackingNumbers[0]").value("SD-"+p.toString().substring(0,8).toUpperCase()));
        var inactive=courier("Inactive","COLOMBO",2);
        mvc.perform(patch("/api/v1/couriers/{id}",inactive.id()).header("Authorization",admin()).contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk());
        assign(parcel(),inactive.id(),false).andExpect(status().isConflict());
        assign(parcel(),UUID.randomUUID(),false).andExpect(status().isNotFound());
        assign(UUID.randomUUID(),c.id(),false).andExpect(status().isNotFound());
    }
    @Test void rejectedRemoteAssignmentReleasesCapacityAndReturnsParcelToQueue() throws Exception {
        var c=courier("Nimal","COLOMBO",1); var p=parcel(); PARCEL_STATUS.set(409);
        assign(p,c.id(),false).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select open_load from couriers where id=?",Integer.class,c.id())).isZero();
        assertThat(jdbc.queryForObject("select state from assignment_commands",String.class)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select count(*) from assignments",Integer.class)).isZero();
        mvc.perform(get("/api/v1/dispatch/queue").header("Authorization",admin())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        PARCEL_STATUS.set(200); assign(p,c.id(),false).andExpect(status().isOk());
    }
    @Test void lostRemoteResponseIsDurableAndRetriesIdempotently() throws Exception {
        var c=courier("Nimal","COLOMBO",1); var p=parcel(); LOSE_RESPONSE.set(true);
        var pending=assign(p,c.id(),false).andExpect(status().isAccepted()).andExpect(header().exists("Location")).andReturn();
        UUID command=UUID.fromString(mapper.readTree(pending.getResponse().getContentAsString()).path("id").asString());
        assertThat(REMOTE.get(p).assignee).isEqualTo(c.userId());
        mvc.perform(get("/api/v1/dispatch/commands/{id}",command).header("Authorization",admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PENDING"));
        mvc.perform(get("/api/v1/dispatch/queue").header("Authorization",admin())).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assign(parcel(),c.id(),false).andExpect(status().isConflict());
        assertThat(service.retry(command).state()).isEqualTo("CONFIRMED");
        assertThat(service.retry(command).state()).isEqualTo("CONFIRMED");
        assertThat(REMOTE.get(p).version).isEqualTo(1);
        assertThat(jdbc.queryForObject("select open_load from couriers where id=?",Integer.class,c.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from assignments",Integer.class)).isEqualTo(1);
    }
    @Test void pendingCommandPreventsDifferentTargetAndDeactivation() throws Exception {
        var c=courier("Nimal","COLOMBO",1); var other=courier("Amal","COLOMBO",1); var p=parcel();
        PARCEL_STATUS.set(503); assign(p,c.id(),false).andExpect(status().isAccepted());
        assign(p,other.id(),true).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/couriers/{id}",c.id()).header("Authorization",admin()).contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.trackingNumbers.length()").value(1));
    }
    @Test void statusProjectionUpdatesManifestPrivacyWorkloadAndStats() throws Exception {
        var c=courier("Nimal","COLOMBO",3); var p=parcel(); assign(p,c.id(),false).andExpect(status().isOk());
        projector.apply(event(p,2,ParcelStatus.PICKED_UP,c.userId(),Instant.now()));
        mvc.perform(get("/api/v1/couriers/me/assignments").header("Authorization","Bearer "+token(c.userId(),"COURIER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].dropoff.phone").value("+94777654321"));
        assign(p,courier("Other","COLOMBO",3).id(),true).andExpect(status().isConflict());
        projector.apply(event(p,3,ParcelStatus.DELIVERED,c.userId(),Instant.now()));
        mvc.perform(get("/api/v1/couriers/me/assignments").header("Authorization","Bearer "+token(c.userId(),"COURIER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/dispatch/workload").header("Authorization",admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.name=='Nimal')].deliveredToday").value(1))
                .andExpect(jsonPath("$[?(@.name=='Nimal')].remainingCapacity").value(3));
        var today=LocalDate.now(ZoneId.of("Asia/Colombo")).toString();
        mvc.perform(get("/api/v1/dispatch/stats/daily").header("Authorization",admin()).param("from",today).param("to",today))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].created").value(1)).andExpect(jsonPath("$[0].completed").value(1));
    }
    @Test void suggestionsAndInvalidRequests() throws Exception {
        courier("Nimal","COLOMBO",2); courier("Amal","KANDY",2); var p=parcel();
        mvc.perform(get("/api/v1/dispatch/suggest").header("Authorization",admin()).param("parcelId",p.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].remainingCapacity").value(2));
        mvc.perform(get("/api/v1/dispatch/suggest").header("Authorization",admin()).param("parcelId",UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/dispatch/commands/{id}",UUID.randomUUID()).header("Authorization",admin())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/dispatch/stats/daily").header("Authorization",admin()).param("from","2026-10-01").param("to","2026-09-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/dispatch/stats/daily").header("Authorization",admin()).param("from","2020-01-01").param("to","2026-01-01")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/dispatch/assignments").header("Authorization",admin()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/dispatch/assignments/{id}",UUID.randomUUID()).header("Authorization",admin()).contentType("application/json").content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/dispatch/assignments/{id}",UUID.randomUUID()).header("Authorization",admin()).contentType("application/json").content("{\"reason\":\"Mistake\"}"))
                .andExpect(status().isNotFound());
    }
}
