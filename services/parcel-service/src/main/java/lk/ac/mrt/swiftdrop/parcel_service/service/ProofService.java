package lk.ac.mrt.swiftdrop.parcel_service.service;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.time.Clock;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.parcel_service.api.*;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelDtos.*;
import lk.ac.mrt.swiftdrop.parcel_service.storage.ProofStorage;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lk.ac.mrt.swiftdrop.parcel_service.domain.DeliveryProof;
import lk.ac.mrt.swiftdrop.parcel_service.repository.ProofRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProofService {
    private final ParcelService parcels;
    private final ProofRepository proofs;
    private final Clock clock;
    private final ProofStorage storage;
    private static final int URL_EXPIRY_SECONDS = 300;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ProofService.class);
    private static final long MAX_BYTES = 5 * 1024 * 1024;

    @Transactional
    public ProofView upload(UUID parcelId, MultipartFile file, Actor actor) {
        var parcel = parcels.operable(parcelId, actor);
        if (parcel.getStatus() != ParcelStatus.OUT_FOR_DELIVERY) {
            throw new ParcelException(ErrorCode.CONFLICT, "Proof can be uploaded when the parcel is out for delivery");
        }
        String filename = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!(filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png"))) {
            throw new ParcelException(ErrorCode.VALIDATION_FAILED, "Proof filename must end in .jpg, .jpeg or .png");
        }
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ParcelException(ErrorCode.VALIDATION_FAILED, "Proof must be a JPEG or PNG image up to 5 MiB");
        }
        byte[] content;
        try { content = file.getBytes(); }
        catch (IOException ex) { throw new ParcelException(ErrorCode.INTERNAL_ERROR, "Could not read delivery proof"); }
        String type = detectType(content);
        validateImage(content);
        if (!type.equals(file.getContentType())) {
            throw new ParcelException(ErrorCode.VALIDATION_FAILED, "Proof content must match its JPEG or PNG content type");
        }
        UUID proofId = UUID.randomUUID();
        String objectKey = "proofs/" + parcelId + "/" + proofId;
        storage.put(objectKey, content, type);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try { storage.delete(objectKey); }
                    catch (RuntimeException ex) { LOG.warn("Could not remove rolled-back proof {}", proofId); }
                }
            }
        });
        var proof = proofs.save(new DeliveryProof(proofId, parcelId, actor.id(), type,
            content.length, clock.instant(), objectKey));
        return new ProofView(proof.getId(), type, content.length, proof.getCreatedAt());
    }

    @Transactional(readOnly=true)
    public ProofDownload download(UUID parcelId, UUID proofId, Actor actor) {
        var proof = get(parcelId, proofId, actor);
        return new ProofDownload(proof.getContentType(), storage.get(proof.getObjectKey()));
    }

    @Transactional(readOnly=true)
    public ProofUrl url(UUID parcelId, UUID proofId, Actor actor) {
        var proof = get(parcelId, proofId, actor);
        return new ProofUrl(storage.presign(proof.getObjectKey(), URL_EXPIRY_SECONDS),
            clock.instant().plusSeconds(URL_EXPIRY_SECONDS));
    }

    private DeliveryProof get(UUID parcelId, UUID proofId, Actor actor) {
        parcels.readable(parcelId, actor);
        return proofs.findById(proofId).filter(p -> p.getParcelId().equals(parcelId)).orElseThrow(() ->
            new ParcelException(ErrorCode.RESOURCE_NOT_FOUND, "Delivery proof was not found"));
    }

    private void validateImage(byte[] bytes) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels < 1 || pixels > 16_000_000) throw new IOException("Invalid image dimensions");
                reader.read(0);
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException ex) {
            throw new ParcelException(ErrorCode.VALIDATION_FAILED, "Proof must be a readable image with at most 16 million pixels");
        }
    }

    private String detectType(byte[] bytes) {
        byte[] png = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), png)) return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255) return "image/jpeg";
        throw new ParcelException(ErrorCode.VALIDATION_FAILED, "Proof must contain JPEG or PNG data");
    }

    public ProofService(ParcelService parcels, ProofRepository proofs, Clock clock, ProofStorage storage) {
        this.parcels = parcels;
        this.proofs = proofs;
        this.clock = clock;
        this.storage = storage;
    }
}
