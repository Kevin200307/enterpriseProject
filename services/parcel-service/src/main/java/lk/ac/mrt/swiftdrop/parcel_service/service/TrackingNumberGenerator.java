package lk.ac.mrt.swiftdrop.parcel_service.service;

import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TrackingNumberGenerator {
    private final JdbcTemplate jdbc;
    public String next() {
        Long sequence = jdbc.queryForObject("select nextval('parcel_tracking_seq')", Long.class);
        return format(sequence);
    }
    public static String format(long sequence) {
        if (sequence < 1 || sequence > 2821109907455L) {
            throw new IllegalStateException("Tracking number sequence exhausted");
        }
        String value = Long.toString(sequence, 36).toUpperCase(Locale.ROOT);
        return "SD-" + "0".repeat(8 - value.length()) + value;
    }

    public TrackingNumberGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
}
