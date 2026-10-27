package lk.ac.mrt.swiftdrop.dispatch_service.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.*;
import lk.ac.mrt.swiftdrop.dispatch_service.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/dispatch") @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
public class DispatchController {
    private final DispatchViews views;
    private final AssignmentService assignments;
    private final AssignmentTransactions transactions;
    private final SuggestionService suggestions;
    public DispatchController(DispatchViews views, AssignmentService assignments, AssignmentTransactions transactions, SuggestionService suggestions) {
        this.views=views; this.assignments=assignments; this.transactions=transactions; this.suggestions=suggestions;
    }
    @GetMapping("/queue") public List<QueueItem> queue(@RequestParam(required=false) String zone) { return views.queue(zone); }
    @GetMapping("/workload") public List<WorkloadRow> workload() { return views.workload(); }
    @GetMapping("/suggest") public List<Suggestion> suggest(@RequestParam UUID parcelId) { return suggestions.suggest(parcelId); }
    @GetMapping("/stats/daily") public List<DailyStat> stats(@RequestParam LocalDate from, @RequestParam LocalDate to) { return views.stats(from,to); }
    @PostMapping("/assignments") public ResponseEntity<?> assign(@Valid @RequestBody AssignRequest input,
            @RequestParam(defaultValue="false") boolean reassign, Authentication auth,
            @RequestHeader(value="X-Request-ID",required=false) String requestId) {
        return response(assignments.assign(input.parcelId(),input.courierId(),reassign,UUID.fromString(auth.getName()),requestId(requestId)));
    }
    @DeleteMapping("/assignments/{id}") public ResponseEntity<?> unassign(@PathVariable UUID id,
            @Valid @RequestBody UnassignRequest input, Authentication auth,
            @RequestHeader(value="X-Request-ID",required=false) String requestId) {
        return response(assignments.unassign(id,UUID.fromString(auth.getName()),requestId(requestId),input.reason()));
    }
    @GetMapping("/commands/{id}") public CommandView command(@PathVariable UUID id) { return transactions.get(id).view(); }
    private ResponseEntity<?> response(CommandView command) {
        if (command != null && command.state().equals("PENDING"))
            return ResponseEntity.accepted().location(URI.create("/api/v1/dispatch/commands/"+command.id())).body(command);
        return ResponseEntity.ok().build();
    }
    private String requestId(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,100}") ? value : UUID.randomUUID().toString();
    }
}
