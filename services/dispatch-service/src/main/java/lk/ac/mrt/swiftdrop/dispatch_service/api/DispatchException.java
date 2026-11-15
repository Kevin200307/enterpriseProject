package lk.ac.mrt.swiftdrop.dispatch_service.api;

import java.util.List;

public class DispatchException extends RuntimeException {
    private final int status;
    private final List<String> trackingNumbers;
    public DispatchException(int status, String message) { this(status,message,List.of()); }
    public DispatchException(int status, String message, List<String> trackingNumbers) {
        super(message); this.status = status; this.trackingNumbers = List.copyOf(trackingNumbers);
    }
    public int status() { return status; }
    public List<String> trackingNumbers() { return trackingNumbers; }
}
