package com.loganalyzer.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single parsed log entry from a REST API or application log file.
 * Supports both JSON-structured logs and plain-text log formats.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogEntry {

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @JsonProperty("timestamp")
    private String rawTimestamp;

    @JsonProperty("level")
    private String logLevel;

    @JsonProperty("message")
    private String message;

    @JsonProperty("statusCode")
    private Integer statusCode;

    @JsonProperty("method")
    private String httpMethod;

    @JsonProperty("endpoint")
    private String endpoint;

    @JsonProperty("service")
    private String serviceName;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("durationMs")
    private Long durationMs;

    @JsonProperty("exception")
    private String exceptionClass;

    @JsonProperty("sqlState")
    private String sqlState;

    // Computed fields (not from JSON)
    private LocalDateTime parsedTimestamp;
    private ErrorType detectedErrorType;
    private String rawLine; // for plain-text logs

    // ─── Constructors ──────────────────────────────────────────────────────────

    public LogEntry() {
    }

    /**
     * Constructor for plain-text log entries parsed manually.
     */
    public LogEntry(String rawLine, LocalDateTime parsedTimestamp, String logLevel,
            String message, Integer statusCode) {
        this.rawLine = rawLine;
        this.parsedTimestamp = parsedTimestamp;
        this.logLevel = logLevel;
        this.message = message;
        this.statusCode = statusCode;
    }

    // ─── Utility Methods ───────────────────────────────────────────────────────

    /**
     * Returns true if this log entry represents an HTTP 4xx client error.
     */
    public boolean isHttpClientError() {
        return statusCode != null && statusCode >= 400 && statusCode <= 499;
    }

    /**
     * Returns true if this log entry represents an HTTP 5xx server error.
     */
    public boolean isHttpServerError() {
        return statusCode != null && statusCode >= 500 && statusCode <= 599;
    }

    /**
     * Returns true if this log entry contains SQL or DB exception indicators.
     */
    public boolean isDatabaseError() {
        if (exceptionClass != null && (exceptionClass.contains("SQLException") ||
                exceptionClass.contains("DataAccessException") ||
                exceptionClass.contains("JdbcException") ||
                exceptionClass.contains("HibernateException"))) {
            return true;
        }
        if (message != null && (message.contains("SQLException") ||
                message.contains("database") ||
                message.contains("DB connection") ||
                message.contains("JDBC") ||
                message.contains("SQL") ||
                message.contains("connection pool") ||
                message.contains("deadlock"))) {
            return true;
        }
        return sqlState != null && !sqlState.isEmpty();
    }

    /**
     * Returns true if this log entry represents any detectable error.
     */
    public boolean isError() {
        return isHttpClientError() || isHttpServerError() || isDatabaseError() ||
                "ERROR".equalsIgnoreCase(logLevel) || "FATAL".equalsIgnoreCase(logLevel);
    }

    public String getFormattedTimestamp() {
        if (parsedTimestamp != null)
            return parsedTimestamp.format(DISPLAY_FORMATTER);
        return rawTimestamp != null ? rawTimestamp : "N/A";
    }

    // ─── Getters & Setters ─────────────────────────────────────────────────────

    public String getRawTimestamp() {
        return rawTimestamp;
    }

    public void setRawTimestamp(String rawTimestamp) {
        this.rawTimestamp = rawTimestamp;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public String getSqlState() {
        return sqlState;
    }

    public void setSqlState(String sqlState) {
        this.sqlState = sqlState;
    }

    public LocalDateTime getParsedTimestamp() {
        return parsedTimestamp;
    }

    public void setParsedTimestamp(LocalDateTime parsedTimestamp) {
        this.parsedTimestamp = parsedTimestamp;
    }

    public ErrorType getDetectedErrorType() {
        return detectedErrorType;
    }

    public void setDetectedErrorType(ErrorType detectedErrorType) {
        this.detectedErrorType = detectedErrorType;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s %s %s (HTTP %s) - %s",
                getFormattedTimestamp(),
                logLevel != null ? logLevel : "?",
                httpMethod != null ? httpMethod : "",
                endpoint != null ? endpoint : "",
                statusCode != null ? statusCode : "N/A",
                message != null ? message : "");
    }
}
