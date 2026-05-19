package si.um.feri.dotaops.backend.opendota.service;

import si.um.feri.dotaops.backend.opendota.domain.OpenDotaErrorCode;

public class OpenDotaClientException extends RuntimeException {

    private final OpenDotaErrorCode errorCode;

    public OpenDotaClientException(OpenDotaErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OpenDotaClientException(OpenDotaErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public OpenDotaErrorCode errorCode() {
        return errorCode;
    }
}
