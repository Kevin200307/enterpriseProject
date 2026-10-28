package lk.ac.mrt.swiftdrop.dispatch_service;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DispatchAuthorizationTest extends IntegrationSupport {
    @Test void anonymousGets401CustomerGets403AndSpoofedIdentityIsIgnored() throws Exception {
        for (String path : List.of("/api/v1/dispatch/queue","/api/v1/dispatch/workload","/api/v1/dispatch/suggest?parcelId="+UUID.randomUUID(),
                "/api/v1/dispatch/stats/daily?from=2026-10-01&to=2026-10-01","/api/v1/couriers","/api/v1/couriers/me/assignments")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate","Bearer"));
            mvc.perform(get(path).header("Authorization","Bearer "+token(UUID.randomUUID(),"CUSTOMER")))
                    .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        var a=courier("A","COLOMBO",2); var b=courier("B","COLOMBO",2);
        assign(parcel(),b.id(),false).andExpect(status().isOk());
        mvc.perform(get("/api/v1/couriers/me/assignments").header("Authorization","Bearer "+token(a.userId(),"COURIER"))
                .header("X-User-ID",b.userId()).param("userId",b.userId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/couriers/{id}/manifest",b.id()).param("date",LocalDate.now().toString())
                .header("Authorization","Bearer "+token(a.userId(),"COURIER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/couriers/{id}/manifest",a.id()).param("date",LocalDate.now().toString())
                .header("Authorization","Bearer "+token(a.userId(),"COURIER"))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/dispatch/assignments").header("Authorization","Bearer "+token(a.userId(),"COURIER"))
                .contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/couriers").header("Authorization","Bearer "+token(a.userId(),"COURIER"))
                .contentType("application/json").content("{}" )).andExpect(status().isForbidden());
    }
    @Test void signedTokensValidateIssuerAudienceUuidSubjectExpiryAndRoles() throws Exception {
        var future=Instant.now().plusSeconds(300);
        var tokens=List.of(token("invalid",BASE,List.of("swiftdrop"),future,List.of("DISPATCHER")),
                token(dispatcher.toString(),"https://wrong.test",List.of("swiftdrop"),future,List.of("DISPATCHER")),
                token(dispatcher.toString(),BASE,List.of("wrong"),future,List.of("DISPATCHER")),
                token(dispatcher.toString(),BASE,List.of("swiftdrop"),Instant.now().minusSeconds(300),List.of("DISPATCHER")),
                token(dispatcher.toString(),BASE,List.of("swiftdrop"),future,List.of("SUPERUSER")),
                token(dispatcher.toString(),BASE,List.of("swiftdrop"),future,List.of()),
                token(dispatcher.toString(),BASE,List.of("swiftdrop"),future,"DISPATCHER"));
        for (var token : tokens) mvc.perform(get("/api/v1/dispatch/queue").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dispatch/queue").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/couriers").header("Authorization","Bearer "+token(dispatcher,"ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
