package lk.ac.mrt.swiftdrop.dispatch_service;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.messaging.*;
import lk.ac.mrt.swiftdrop.dispatch_service.messaging.ParcelListener;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties={"dispatch.messaging-enabled=true","spring.rabbitmq.dynamic=true"})
class RabbitProjectionIntegrationTest extends IntegrationSupport {
    @Autowired RabbitTemplate rabbit;
    @MockitoSpyBean ParcelListener listener;
    @Test void createdEventAppearsOnceWithinOneSecondThenAssignsIntoManifest() throws Exception {
        var courier=courier("A","COLOMBO",2); UUID id=UUID.randomUUID(); REMOTE.put(id,new RemoteParcel(id));
        var event=event(id,0,ParcelStatus.CREATED,null,Instant.now());
        long start=System.nanoTime(); send(mapper.writeValueAsBytes(event));
        await(() -> jdbc.queryForObject("select count(*) from dispatch_parcels where id=?",Integer.class,id)==1,1000);
        assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(1));
        send(mapper.writeValueAsBytes(event));
        await(() -> jdbc.queryForObject("select count(*) from processed_events where event_id=?",Integer.class,event.eventId())==1,1000);
        assign(id,courier.id(),false).andExpect(status().isOk());
        mvc.perform(get("/api/v1/couriers/me/assignments").header("Authorization","Bearer "+token(courier.userId(),"COURIER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].parcelId").value(id.toString()));
        assertThat(jdbc.queryForObject("select count(*) from dispatch_parcels",Integer.class)).isEqualTo(1);
    }
    @Test void poisonRetriesThreeTimesThenDeadLettersAndLaterEventsProceed() throws Exception {
        var dlq=Queues.deadLetterOf(Queues.DISPATCH_PARCEL_DISPATCH_CHANGED);
        while (rabbit.receive(dlq)!=null) { }
        byte[] poison="{not json}".getBytes(StandardCharsets.UTF_8); send(poison);
        Message dead=rabbit.receive(dlq,10000); assertThat(dead).isNotNull(); assertThat(dead.getBody()).isEqualTo(poison);
        verify(listener,times(3)).receive(argThat(m -> Arrays.equals(m.getBody(),poison)));
        UUID p=UUID.randomUUID(); send(mapper.writeValueAsBytes(event(p,0,ParcelStatus.CREATED,null,Instant.now())));
        await(() -> jdbc.queryForObject("select count(*) from dispatch_parcels where id=?",Integer.class,p)==1,2000);
    }
    @Test void transientFailureRetriesAndOutOfOrderMessagesDoNotRegress() throws Exception {
        UUID p=UUID.randomUUID(); var newest=event(p,4,ParcelStatus.DELIVERED,null,Instant.now());
        var count=new AtomicInteger();
        doAnswer(invocation -> { if (count.incrementAndGet()<3) throw new IllegalStateException("Temporary failure"); return invocation.callRealMethod(); })
                .when(listener).receive(argThat(m -> new String(m.getBody(),StandardCharsets.UTF_8).contains(newest.eventId().toString())));
        send(mapper.writeValueAsBytes(newest));
        await(() -> jdbc.queryForObject("select count(*) from dispatch_parcels where id=?",Integer.class,p)==1,3000);
        assertThat(count).hasValue(3);
        send(mapper.writeValueAsBytes(event(p,0,ParcelStatus.CREATED,null,Instant.now().plusSeconds(1))));
        await(() -> jdbc.queryForObject("select count(*) from processed_events",Integer.class)==2,2000);
        assertThat(jdbc.queryForObject("select status from dispatch_parcels where id=?",String.class,p)).isEqualTo("DELIVERED");
    }
    private void send(byte[] bytes) {
        var properties=new MessageProperties(); properties.setContentType("application/json"); properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        rabbit.send(Exchanges.PARCEL_EVENTS,RoutingKeys.PARCEL_DISPATCH_CHANGED_V1,new Message(bytes,properties));
    }
    private void await(BooleanSupplier condition,long timeout) throws InterruptedException {
        long deadline=System.nanoTime()+timeout*1_000_000;
        while (!condition.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
