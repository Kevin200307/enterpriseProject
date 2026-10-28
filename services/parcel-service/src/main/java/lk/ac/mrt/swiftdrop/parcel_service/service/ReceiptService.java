package lk.ac.mrt.swiftdrop.parcel_service.service;

import java.io.*;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;

@Service
public class ReceiptService {
    private final ParcelService parcels;

    public byte[] generate(UUID id, Actor actor) {
        var p = parcels.get(id, actor);
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.setLeading(20);
                stream.newLineAtOffset(50, 740);
                // Keep receipts free of recipient PII; addresses remain in authorized parcel details.
                for (String line : new String[]{"SwiftDrop booking receipt", "Tracking: " + p.trackingNumber(),
                    "Booked: " + p.createdAt(), "Package: " + p.size(), "Weight (kg): " + p.weightKg(),
                    "Delivery fee: " + p.fee() + " " + p.currency(), "Status: " + p.status()}) {
                    stream.showText(line);
                    stream.newLine();
                }
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ParcelException(ErrorCode.INTERNAL_ERROR, "Could not generate receipt");
        }
    }

    public ReceiptService(ParcelService parcels) {
        this.parcels = parcels;
    }
}
