package com.loganalyzer.models;

/**
 * Enumeration of error types detectable in REST API and application logs.
 * Used for root cause classification and ticket type assignment.
 */
public enum ErrorType {

    // HTTP Client Errors (4xx)
    HTTP_400_BAD_REQUEST("HTTP 400 Bad Request", "Client sent malformed or invalid request", Priority.LOW),
    HTTP_401_UNAUTHORIZED("HTTP 401 Unauthorized", "Authentication token missing or invalid", Priority.HIGH),
    HTTP_403_FORBIDDEN("HTTP 403 Forbidden", "Client lacks permission to access resource", Priority.HIGH),
    HTTP_404_NOT_FOUND("HTTP 404 Not Found", "Requested resource does not exist", Priority.LOW),
    HTTP_405_METHOD_NOT_ALLOWED("HTTP 405 Method Not Allowed", "HTTP method not supported by endpoint", Priority.LOW),
    HTTP_408_REQUEST_TIMEOUT("HTTP 408 Request Timeout", "Client request exceeded server timeout", Priority.MEDIUM),
    HTTP_409_CONFLICT("HTTP 409 Conflict", "Resource conflict due to concurrent modification", Priority.MEDIUM),
    HTTP_422_UNPROCESSABLE_ENTITY("HTTP 422 Unprocessable Entity", "Request body semantically invalid", Priority.MEDIUM),
    HTTP_429_TOO_MANY_REQUESTS("HTTP 429 Too Many Requests", "Rate limit exceeded by client", Priority.HIGH),

    // HTTP Server Errors (5xx)
    HTTP_500_INTERNAL_SERVER_ERROR("HTTP 500 Internal Server Error", "Unexpected server-side failure", Priority.CRITICAL),
    HTTP_502_BAD_GATEWAY("HTTP 502 Bad Gateway", "Upstream service returned invalid response", Priority.CRITICAL),
    HTTP_503_SERVICE_UNAVAILABLE("HTTP 503 Service Unavailable", "Service temporarily down or overloaded", Priority.CRITICAL),
    HTTP_504_GATEWAY_TIMEOUT("HTTP 504 Gateway Timeout", "Upstream service did not respond in time", Priority.HIGH),

    // Database / SQL Errors
    SQL_EXCEPTION("SQL Exception", "Database query execution failed", Priority.HIGH),
    SQL_SYNTAX_ERROR("SQL Syntax Error", "Invalid SQL query syntax detected", Priority.HIGH),
    SQL_CONSTRAINT_VIOLATION("SQL Constraint Violation", "Database integrity constraint violated", Priority.MEDIUM),
    SQL_DEADLOCK("SQL Deadlock Detected", "Concurrent database transaction deadlock", Priority.CRITICAL),

    // Database Connectivity
    DB_CONNECTION_FAILURE("Database Connection Failure", "Unable to establish database connection", Priority.CRITICAL),
    DB_CONNECTION_TIMEOUT("Database Connection Timeout", "Database connection pool exhausted or timed out", Priority.CRITICAL),
    DB_AUTHENTICATION_FAILURE("Database Authentication Failure", "Invalid database credentials", Priority.CRITICAL),

    // Generic / Unknown
    UNKNOWN("Unknown Error", "Unclassified error requiring manual investigation", Priority.MEDIUM);

    private final String displayName;
    private final String rootCauseDescription;
    private final Priority defaultPriority;

    ErrorType(String displayName, String rootCauseDescription, Priority defaultPriority) {
        this.displayName = displayName;
        this.rootCauseDescription = rootCauseDescription;
        this.defaultPriority = defaultPriority;
    }

    public String getDisplayName() { return displayName; }
    public String getRootCauseDescription() { return rootCauseDescription; }
    public Priority getDefaultPriority() { return defaultPriority; }

    /**
     * Returns true if this error type represents a server-side 5xx error.
     */
    public boolean isServerError() {
        return name().startsWith("HTTP_5") || name().startsWith("DB_") || name().startsWith("SQL_DEADLOCK");
    }

    /**
     * Returns true if this error type represents a client-side 4xx error.
     */
    public boolean isClientError() {
        return name().startsWith("HTTP_4");
    }

    /**
     * Returns true if this error type is database-related.
     */
    public boolean isDatabaseError() {
        return name().startsWith("SQL_") || name().startsWith("DB_");
    }

    @Override
    public String toString() {
        return displayName;
    }
}
