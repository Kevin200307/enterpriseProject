package lk.ac.mrt.swiftdrop.parcel_service.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelDtos.*;
import lk.ac.mrt.swiftdrop.parcel_service.service.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/parcels")
public class ParcelController {
    private final ParcelService parcels;
    private final ProofService proofs;
    private final ReceiptService receipts;

    @PostMapping
    public ResponseEntity<ParcelView> book(@Valid @RequestBody BookingRequest request,
        @RequestHeader("Idempotency-Key") String key, Authentication auth) {
        var result = parcels.book(request, key, Actor.from(auth));
        return ResponseEntity.created(URI.create("/api/v1/parcels/" + result.id())).body(result);
    }
    @GetMapping
    public PageView<ParcelView> list(@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, Authentication auth) {
        return parcels.list(page, size, Actor.from(auth));
    }
    @GetMapping("/{id}")
    public ParcelView get(@PathVariable UUID id, Authentication auth) { return parcels.get(id, Actor.from(auth)); }

    @GetMapping("/by-tracking/{trackingNumber}")
    public lk.ac.mrt.swiftdrop.contracts.events.ParcelTrackingSnapshotV1 getByTrackingNumber(@PathVariable String trackingNumber) {
        return parcels.getSnapshotByTrackingNumber(trackingNumber);
    }

    @PutMapping("/{id}/assignment")
    public ParcelView assign(@PathVariable UUID id, @Valid @RequestBody AssignmentRequest request, Authentication auth) {
        return parcels.assign(id, request.courierId(), Actor.from(auth));
    }
    @PatchMapping("/{id}/status")
    public ParcelView status(@PathVariable UUID id, @Valid @RequestBody StatusRequest request, Authentication auth) {
        return parcels.changeStatus(id, request, Actor.from(auth));
    }
    @GetMapping("/{id}/history")
    public PageView<HistoryView> history(@PathVariable UUID id, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, Authentication auth) {
        return parcels.history(id, page, size, Actor.from(auth));
    }
    @PostMapping(value="/{id}/proofs", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProofView> upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file, Authentication auth) {
        var proof = proofs.upload(id, file, Actor.from(auth));
        return ResponseEntity.created(URI.create("/api/v1/parcels/" + id + "/proofs/" + proof.id())).body(proof);
    }
    @GetMapping("/{id}/proofs/{proofId}")
    public ResponseEntity<byte[]> proof(@PathVariable UUID id, @PathVariable UUID proofId, Authentication auth) {
        var proof = proofs.download(id, proofId, Actor.from(auth));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(proof.contentType()))
            .cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=delivery-proof")
            .body(proof.content());
    }
    @GetMapping("/{id}/proofs/{proofId}/url")
    public ResponseEntity<ProofUrl> proofUrl(@PathVariable UUID id, @PathVariable UUID proofId, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(proofs.url(id, proofId, Actor.from(auth)));
    }
    @GetMapping(value="/{id}/receipt", produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=receipt-" + id + ".pdf")
            .body(receipts.generate(id, Actor.from(auth)));
    }

    public ParcelController(ParcelService parcels, ProofService proofs, ReceiptService receipts) {
        this.parcels = parcels;
        this.proofs = proofs;
        this.receipts = receipts;
    }
}
