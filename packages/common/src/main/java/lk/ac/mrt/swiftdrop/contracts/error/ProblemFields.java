package lk.ac.mrt.swiftdrop.contracts.error;

/**
 * Field names of the error response body. The first five are the RFC 7807 standard members;
 * the rest are SwiftDrop extensions every service must use with exactly these names.
 */
public final class ProblemFields {

    public static final String TYPE = "type";
    public static final String TITLE = "title";
    public static final String STATUS = "status";
    public static final String DETAIL = "detail";
    public static final String INSTANCE = "instance";

    public static final String ERROR_CODE = "errorCode";
    public static final String TRACE_ID = "traceId";
    public static final String TIMESTAMP = "timestamp";
    /** List of {@link FieldViolation}, present only for {@link ErrorCode#VALIDATION_FAILED}. */
    public static final String VIOLATIONS = "violations";

    private ProblemFields() {
    }
}
