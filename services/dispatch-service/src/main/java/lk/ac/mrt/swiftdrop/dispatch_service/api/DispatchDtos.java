package lk.ac.mrt.swiftdrop.dispatch_service.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.domain.*;

public final class DispatchDtos {
    private DispatchDtos() {}
    public enum VehicleType { BIKE, THREEWHEEL, VAN }
    public record Courier(UUID id, UUID userId, String name, VehicleType vehicleType,
            String homeZoneCode, String phone, int maxDailyLoad, boolean active) {}
    public record CourierInput(@NotNull UUID userId, @NotNull VehicleType vehicleType,
            @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_-]{0,79}") String homeZoneCode,
            @NotBlank @Pattern(regexp="(?:\\+94|0)[1-9][0-9]{8}") String phone,
            @NotNull @Min(1) @Max(10000) Integer maxDailyLoad, Boolean active) {}
    public record CourierPatch(UUID userId, VehicleType vehicleType,
            @Pattern(regexp="[A-Z][A-Z0-9_-]{0,79}") String homeZoneCode,
            @Pattern(regexp="(?:\\+94|0)[1-9][0-9]{8}") String phone,
            @Min(1) @Max(10000) Integer maxDailyLoad, Boolean active) {}
    public record QueueItem(UUID parcelId, String trackingNumber, String pickupCity,
            PackageSize size, BigDecimal weightKg, Instant createdAt) {}
    public record Suggestion(UUID courierId, String name, String reason, long remainingCapacity) {}
    public record WorkloadRow(UUID courierId, String name, boolean active, long activeAssignments,
            long deliveredToday, long failedToday, long remainingCapacity) {}
    public record Address(String contactName, String phone, String line1, String line2,
            String city, String postalCode) {}
    public record Assignment(UUID id, UUID parcelId, String trackingNumber, ParcelStatus status,
            Address pickup, Address dropoff, PackageSize size, BigDecimal weightKg, Instant assignedAt) {}
    public record DailyStat(LocalDate date, long created, long completed, long failed) {}
    public record PageView<T>(List<T> content, long totalElements, int totalPages, int number, int size) {}
    public record AssignRequest(@NotNull UUID parcelId, @NotNull UUID courierId) {}
    public record UnassignRequest(@NotBlank @Size(max=1000) String reason) {}
    public record CommandView(UUID id, String state, UUID parcelId, UUID courierId, String detail) {}
}
