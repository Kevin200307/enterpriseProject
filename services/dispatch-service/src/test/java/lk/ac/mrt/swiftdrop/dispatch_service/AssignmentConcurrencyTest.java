package lk.ac.mrt.swiftdrop.dispatch_service;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AssignmentConcurrencyTest extends IntegrationSupport {
    @Test void twoDispatchersCannotOverbookOneCourier() throws Exception {
        var c=courier("Nimal","COLOMBO",1); var first=parcel(); var second=parcel();
        var results=race(() -> code(first,c.id()),() -> code(second,c.id()));
        assertThat(results).containsExactlyInAnyOrder(200,409);
        assertThat(jdbc.queryForObject("select open_load from couriers where id=?",Integer.class,c.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from assignments where closed_at is null",Integer.class)).isEqualTo(1);
    }
    @Test void twoDispatchersCannotAssignOneParcelToDifferentCouriers() throws Exception {
        var a=courier("A","COLOMBO",2); var b=courier("B","COLOMBO",2); var p=parcel();
        assertThat(race(() -> code(p,a.id()),() -> code(p,b.id()))).containsExactlyInAnyOrder(200,409);
        assertThat(jdbc.queryForObject("select sum(open_load) from couriers",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from assignments where active_parcel_id=?",Integer.class,p)).isEqualTo(1);
    }
    @Test void concurrentSameCourierRequestsAreIdempotent() throws Exception {
        var a=courier("A","COLOMBO",1); var p=parcel();
        assertThat(race(() -> code(p,a.id()),() -> code(p,a.id()))).containsExactly(200,200);
        assertThat(REMOTE.get(p).version).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from assignments",Integer.class)).isEqualTo(1);
    }
    private int code(UUID p, UUID c) { try { return assign(p,c,false).andReturn().getResponse().getStatus(); } catch (Exception ex) { throw new RuntimeException(ex); } }
    private List<Integer> race(IntSupplier first, IntSupplier second) throws Exception {
        try (var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=pool.submit(() -> { start.await(); return first.getAsInt(); });
            var b=pool.submit(() -> { start.await(); return second.getAsInt(); });
            start.countDown(); return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
}
