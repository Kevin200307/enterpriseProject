package lk.ac.mrt.swiftdrop.identity_service.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, String.format("%s with identifier '%s' was not found", resourceName, identifier));
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
