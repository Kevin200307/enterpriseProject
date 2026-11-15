package lk.ac.mrt.swiftdrop.dispatch_service.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchException;
import lk.ac.mrt.swiftdrop.dispatch_service.domain.AssignmentCommand;

@Component
public class ParcelClient {
    private final RestClient client;
    private final ServiceTokenProvider tokens;
    public ParcelClient(@Value("${dispatch.parcel-url}") String url, ServiceTokenProvider tokens) {
        client = HttpClients.create(url); this.tokens = tokens;
    }
    public record Request(UUID commandId, UUID expectedAssignee, UUID assigneeUserId, UUID requestedBy, String reason) {}
    public record Result(UUID parcelId, UUID assigneeUserId, long parcelVersion) {}
    public Result apply(AssignmentCommand command) {
        try {
            var result = client.put().uri("/api/v1/internal/parcels/{id}/assignment",command.getParcelId())
                    .headers(h -> { h.setBearerAuth(tokens.token()); h.set("X-Request-ID",command.getRequestId()); })
                    .body(new Request(command.getId(),command.getExpectedAssignee(),command.getTargetAssignee(),
                            command.getRequestedBy(),command.getReason())).retrieve().body(Result.class);
            if (result == null || !command.getParcelId().equals(result.parcelId())
                    || !java.util.Objects.equals(command.getTargetAssignee(),result.assigneeUserId()))
                throw new DispatchException(503,"Parcel service returned an invalid confirmation");
            return result;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 400 || status == 404 || status == 409 || status == 422)
                throw new DispatchException(status == 422 ? 400 : status,"Parcel service rejected the assignment");
            throw new DispatchException(503,"Parcel service is unavailable; assignment remains pending");
        } catch (ResourceAccessException ex) { throw new DispatchException(503,"Parcel service is unavailable; assignment remains pending"); }
    }
}
