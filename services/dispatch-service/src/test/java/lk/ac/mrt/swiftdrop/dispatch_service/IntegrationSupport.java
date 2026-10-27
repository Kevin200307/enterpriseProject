package lk.ac.mrt.swiftdrop.dispatch_service;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lk.ac.mrt.swiftdrop.contracts.domain.*;
import lk.ac.mrt.swiftdrop.contracts.events.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.*;
import lk.ac.mrt.swiftdrop.dispatch_service.messaging.ParcelProjector;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"dispatch.messaging-enabled=false","dispatch.retry-enabled=false","spring.rabbitmq.dynamic=false"})
@AutoConfigureMockMvc
abstract class IntegrationSupport {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final RSAKey KEY;
    static final HttpServer UPSTREAM;
    static final String BASE;
    static final Map<UUID,RemoteParcel> REMOTE = new ConcurrentHashMap<>();
    static final Map<UUID,Map<String,Object>> USERS = new ConcurrentHashMap<>();
    static final Map<UUID,String> RECEIPTS = new ConcurrentHashMap<>();
    static final AtomicInteger PARCEL_STATUS = new AtomicInteger(200);
    static final AtomicInteger IDENTITY_STATUS = new AtomicInteger(200);
    static final AtomicInteger CALLS = new AtomicInteger();
    static final AtomicBoolean LOSE_RESPONSE = new AtomicBoolean();
    static final AtomicReference<String> REQUEST_ID = new AtomicReference<>();
    static final String SECRET = "dispatch-integration-secret-32-characters";
    static {
        try {
            POSTGRES.start(); RABBIT.start();
            KEY = new RSAKeyGenerator(2048).keyID("dispatch-test").generate();
            UPSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            UPSTREAM.setExecutor(Executors.newCachedThreadPool(r -> { var t=new Thread(r); t.setDaemon(true); return t; }));
            BASE = "http://127.0.0.1:"+UPSTREAM.getAddress().getPort();
            UPSTREAM.createContext("/",exchange -> {
                int status=200; String body="{}";
                String path=exchange.getRequestURI().getPath();
                try {
                    if (path.equals("/jwks")) body = new JWKSet(KEY.toPublicJWK()).toString();
                    else if (path.equals("/api/v1/auth/service-token")) {
                        if (!("Basic "+Base64.getEncoder().encodeToString(("dispatch-service:"+SECRET).getBytes(StandardCharsets.UTF_8)))
                                .equals(exchange.getRequestHeaders().getFirst("Authorization"))) status=401;
                        else body=JSON.writeValueAsString(Map.of("access_token","service-token","expires_in",300));
                    } else if (!"Bearer service-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) status=401;
                    else if (path.startsWith("/api/v1/internal/users/")) {
                        UUID id=UUID.fromString(path.substring(path.lastIndexOf('/')+1));
                        status=IDENTITY_STATUS.get();
                        if (status==200) { var user=USERS.get(id); if (user==null) status=404; else body=JSON.writeValueAsString(user); }
                    } else if (path.matches("/api/v1/internal/parcels/[^/]+/assignment")) {
                        CALLS.incrementAndGet(); REQUEST_ID.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
                        status=PARCEL_STATUS.get();
                        if (status==200) {
                            var request=JSON.readTree(exchange.getRequestBody());
                            UUID command=UUID.fromString(request.path("commandId").asString());
                            var parcel=REMOTE.get(UUID.fromString(path.split("/")[5]));
                            if (parcel==null) status=404;
                            else synchronized(parcel) {
                                if (RECEIPTS.containsKey(command)) body=RECEIPTS.get(command);
                                else {
                                    UUID expected=request.path("expectedAssignee").isNull()?null:UUID.fromString(request.path("expectedAssignee").asString());
                                    if (!Objects.equals(expected,parcel.assignee)) status=409;
                                    else {
                                        parcel.assignee=request.path("assigneeUserId").isNull()?null:UUID.fromString(request.path("assigneeUserId").asString());
                                        parcel.version++;
                                        Map<String,Object> result=new HashMap<>(); result.put("parcelId",parcel.id);
                                        result.put("assigneeUserId",parcel.assignee); result.put("parcelVersion",parcel.version);
                                        body=JSON.writeValueAsString(result); RECEIPTS.put(command,body);
                                        if (LOSE_RESPONSE.getAndSet(false)) status=503;
                                    }
                                }
                            }
                        }
                    } else status=404;
                } catch (Exception ex) { status=500; body="{}"; }
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            UPSTREAM.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    static class RemoteParcel {
        final UUID id; UUID assignee; long version;
        RemoteParcel(UUID id) { this.id=id; }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl); r.add("spring.datasource.username",POSTGRES::getUsername);
        r.add("spring.datasource.password",POSTGRES::getPassword);
        r.add("spring.rabbitmq.host",RABBIT::getHost); r.add("spring.rabbitmq.port",RABBIT::getAmqpPort);
        r.add("spring.rabbitmq.username",RABBIT::getAdminUsername); r.add("spring.rabbitmq.password",RABBIT::getAdminPassword);
        r.add("dispatch.identity-url",() -> BASE); r.add("dispatch.parcel-url",() -> BASE);
        r.add("dispatch.security.issuer",() -> BASE); r.add("dispatch.security.jwks",() -> BASE+"/jwks");
        r.add("dispatch.client-secret",() -> SECRET);
    }
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ParcelProjector projector;
    final UUID dispatcher=UUID.randomUUID();
    @BeforeEach void resetData() {
        jdbc.execute("truncate assignment_commands,assignments,dispatch_parcels,couriers,processed_events cascade");
        REMOTE.clear(); USERS.clear(); RECEIPTS.clear(); PARCEL_STATUS.set(200); IDENTITY_STATUS.set(200);
        CALLS.set(0); LOSE_RESPONSE.set(false);
    }
    static String token(UUID user, String... roles) {
        return token(user.toString(),BASE,List.of("swiftdrop"),Instant.now().plusSeconds(300),List.of(roles));
    }
    static String token(String sub, String issuer, List<String> audience, Instant expiry, Object roles) {
        var claims=JwtClaimsSet.builder().issuer(issuer).audience(audience).subject(sub)
                .issuedAt(Instant.now().minusSeconds(600)).expiresAt(expiry).claim("roles",roles).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(KEY)))
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build(),claims)).getTokenValue();
    }
    String admin() { return "Bearer "+token(dispatcher,"DISPATCHER"); }
    Courier courier(String name, String zone, int capacity) throws Exception {
        UUID user=UUID.randomUUID(); USERS.put(user,Map.of("id",user,"fullName",name,"roles",Set.of("COURIER"),"status","ACTIVE"));
        return mapper.readValue(mvc.perform(post("/api/v1/couriers").header("Authorization",admin())
                .contentType("application/json").content(mapper.writeValueAsString(new CourierInput(user,VehicleType.BIKE,zone,"+94771234567",capacity,true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),Courier.class);
    }
    EventEnvelope<ParcelDispatchChangedV1> event(UUID id, long version, ParcelStatus status, UUID assignee, Instant time) {
        return new EventEnvelope<>(UUID.randomUUID(),ParcelDispatchChangedV1.EVENT_TYPE,1,time,"parcel-service",
                new ParcelDispatchChangedV1(id,"SD-"+id.toString().substring(0,8).toUpperCase(),version,status,assignee,
                    new ParcelDispatchChangedV1.Address("Sender","+94771234567","1 Main",null,"Colombo","00100","COLOMBO"),
                    new ParcelDispatchChangedV1.Address("Recipient","+94777654321","2 Lake",null,"Kandy","20000","KANDY"),
                    PackageSize.SMALL,new java.math.BigDecimal("1.25"),time.minusSeconds(60),null));
    }
    UUID parcel() {
        UUID id=UUID.randomUUID(); REMOTE.put(id,new RemoteParcel(id));
        projector.apply(event(id,0,ParcelStatus.CREATED,null,Instant.now())); return id;
    }
    ResultActions assign(UUID parcel, UUID courier, boolean reassign) throws Exception {
        return mvc.perform(post("/api/v1/dispatch/assignments").param("reassign",String.valueOf(reassign))
                .header("Authorization",admin()).header("X-Request-ID","dispatch-api-test")
                .contentType("application/json").content(mapper.writeValueAsString(new AssignRequest(parcel,courier))));
    }
}
