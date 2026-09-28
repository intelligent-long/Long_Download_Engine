package com.longx.intelligent.lib.longdownloadengine.core.exception;

import java.io.Serial;

/**
 * Created by LONG on 2026/9/6 at 00:52.
 */
public class NotSupportRangeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotSupportRangeException() {
        super("Server does not support HTTP Range requests.");
    }

    public NotSupportRangeException(String message) {
        super(message);
    }

    public NotSupportRangeException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotSupportRangeException(Throwable cause) {
        super(cause);
    }
}
