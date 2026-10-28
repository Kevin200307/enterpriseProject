package lk.ac.mrt.swiftdrop.dispatch_service.service;

import java.util.UUID;
import lk.ac.mrt.swiftdrop.dispatch_service.api.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.CommandView;
import lk.ac.mrt.swiftdrop.dispatch_service.client.ParcelClient;
import lk.ac.mrt.swiftdrop.dispatch_service.domain.AssignmentCommand;
import org.springframework.stereotype.Service;

@Service
public class AssignmentService {
    private final AssignmentTransactions transactions;
    private final ParcelClient parcels;
    public AssignmentService(AssignmentTransactions transactions, ParcelClient parcels) { this.transactions=transactions; this.parcels=parcels; }
    public CommandView assign(UUID parcel, UUID courier, boolean reassign, UUID actor, String requestId) {
        return execute(transactions.prepare(parcel,courier,reassign,actor,requestId));
    }
    public CommandView unassign(UUID id, UUID actor, String requestId, String reason) {
        return execute(transactions.prepareUnassign(id,actor,requestId,reason));
    }
    public CommandView retry(UUID id) { return execute(transactions.get(id)); }
    private CommandView execute(AssignmentCommand command) {
        if (command == null) return null;
        if (!command.getState().equals("PENDING")) return command.view();
        try { return transactions.confirm(command.getId(),parcels.apply(command)).view(); }
        catch (DispatchException ex) {
            if (ex.status() == 503) return transactions.get(command.getId()).view();
            transactions.reject(command.getId(),ex.getMessage()); throw ex;
        }
    }
}
