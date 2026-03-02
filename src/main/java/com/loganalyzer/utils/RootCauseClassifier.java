package com.loganalyzer.utils;

import com.loganalyzer.models.ErrorType;
import com.loganalyzer.models.LogEntry;
import com.loganalyzer.models.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classifies detected log errors into specific {@link ErrorType} categories
 * by analyzing HTTP status codes, exception class names, and message content.
 * Applies rule-based, deterministic root cause analysis.
 */
public class RootCauseClassifier {

    private static final Logger logger = LoggerFactory.getLogger(RootCauseClassifier.class);

    private RootCauseClassifier() {
    }

    /**
     * Classifies a log entry into an {@link ErrorType} and sets it on the entry.
     *
     * @param entry the log entry to classify
     * @return the determined ErrorType
     */
    public static ErrorType classify(LogEntry entry) {
        ErrorType type = determineErrorType(entry);
        entry.setDetectedErrorType(type);
        logger.debug("Classified log entry as [{}]: {}", type, entry.getMessage());
        return type;
    }

    private static ErrorType determineErrorType(LogEntry entry) {
        // ── 1. DB/SQL exceptions take precedence over HTTP codes ────────────────
        if (isDbConnectionFailure(entry))
            return ErrorType.DB_CONNECTION_FAILURE;
        if (isDbConnectionTimeout(entry))
            return ErrorType.DB_CONNECTION_TIMEOUT;
        if (isDbAuthFailure(entry))
            return ErrorType.DB_AUTHENTICATION_FAILURE;
        if (isSqlDeadlock(entry))
            return ErrorType.SQL_DEADLOCK;
        if (isSqlSyntaxError(entry))
            return ErrorType.SQL_SYNTAX_ERROR;
        if (isSqlConstraintViolation(entry))
            return ErrorType.SQL_CONSTRAINT_VIOLATION;
        if (isSqlException(entry))
            return ErrorType.SQL_EXCEPTION;

        // ── 2. HTTP status code classification ──────────────────────────────────
        if (entry.getStatusCode() != null) {
            return classifyHttpCode(entry.getStatusCode(), entry);
        }

        // ── 3. Message-based fallback ───────────────────────────────────────────
        return classifyByMessage(entry);
    }

    private static ErrorType classifyHttpCode(int code, LogEntry entry) {
        return switch (code) {
            case 400 -> ErrorType.HTTP_400_BAD_REQUEST;
            case 401 -> ErrorType.HTTP_401_UNAUTHORIZED;
            case 403 -> ErrorType.HTTP_403_FORBIDDEN;
            case 404 -> ErrorType.HTTP_404_NOT_FOUND;
            case 405 -> ErrorType.HTTP_405_METHOD_NOT_ALLOWED;
            case 408 -> ErrorType.HTTP_408_REQUEST_TIMEOUT;
            case 409 -> ErrorType.HTTP_409_CONFLICT;
            case 422 -> ErrorType.HTTP_422_UNPROCESSABLE_ENTITY;
            case 429 -> ErrorType.HTTP_429_TOO_MANY_REQUESTS;
            case 500 -> ErrorType.HTTP_500_INTERNAL_SERVER_ERROR;
            case 502 -> ErrorType.HTTP_502_BAD_GATEWAY;
            case 503 -> ErrorType.HTTP_503_SERVICE_UNAVAILABLE;
            case 504 -> ErrorType.HTTP_504_GATEWAY_TIMEOUT;
            default -> {
                if (code >= 400 && code < 500)
                    yield ErrorType.HTTP_400_BAD_REQUEST;
                if (code >= 500)
                    yield ErrorType.HTTP_500_INTERNAL_SERVER_ERROR;
                yield ErrorType.UNKNOWN;
            }
        };
    }

    private static ErrorType classifyByMessage(LogEntry entry) {
        String msg = entry.getMessage() != null ? entry.getMessage().toLowerCase() : "";
        String ex = entry.getExceptionClass() != null ? entry.getExceptionClass().toLowerCase() : "";
        String combined = msg + " " + ex;

        if (combined.contains("unauthorized") || combined.contains("authentication"))
            return ErrorType.HTTP_401_UNAUTHORIZED;
        if (combined.contains("forbidden") || combined.contains("permission denied"))
            return ErrorType.HTTP_403_FORBIDDEN;
        if (combined.contains("not found"))
            return ErrorType.HTTP_404_NOT_FOUND;
        if (combined.contains("timeout") && combined.contains("gateway"))
            return ErrorType.HTTP_504_GATEWAY_TIMEOUT;
        if (combined.contains("service unavailable"))
            return ErrorType.HTTP_503_SERVICE_UNAVAILABLE;
        if (combined.contains("internal server error"))
            return ErrorType.HTTP_500_INTERNAL_SERVER_ERROR;
        return ErrorType.UNKNOWN;
    }

    // ─── DB/SQL Detection Helpers ──────────────────────────────────────────────

    private static boolean isDbConnectionFailure(LogEntry e) {
        return containsAny(e, "connection refused", "unable to connect", "db connection failed",
                "cannot connect to database", "connection pool exhausted", "no suitable driver");
    }

    private static boolean isDbConnectionTimeout(LogEntry e) {
        return containsAny(e, "connection timeout", "connection timed out", "pool timed out",
                "hikaripool", "hikari pool", "bonecp");
    }

    private static boolean isDbAuthFailure(LogEntry e) {
        return containsAny(e, "access denied for user", "authentication failed for user",
                "invalid password", "ora-01017", "password authentication failed");
    }

    private static boolean isSqlDeadlock(LogEntry e) {
        return containsAny(e, "deadlock", "lock timeout exceeded", "lock wait timeout");
    }

    private static boolean isSqlSyntaxError(LogEntry e) {
        return containsAny(e, "syntax error", "sqlsyntaxerrorexception", "sql syntax",
                "you have an error in your sql syntax", "ora-00900");
    }

    private static boolean isSqlConstraintViolation(LogEntry e) {
        return containsAny(e, "constraint violation", "constraintviolationexception",
                "unique constraint", "foreign key constraint", "integrity constraint",
                "sqlintegrityconstraintviolationexception");
    }

    private static boolean isSqlException(LogEntry e) {
        String ex = e.getExceptionClass() != null ? e.getExceptionClass() : "";
        return ex.contains("SQLException") || ex.contains("DataAccessException") ||
                ex.contains("JdbcSQLException") || ex.contains("HibernateException") ||
                containsAny(e, "sqlexception", "sql error", "jdbc error");
    }

    private static boolean containsAny(LogEntry entry, String... keywords) {
        String msg = (entry.getMessage() != null ? entry.getMessage() : "").toLowerCase();
        String ex = (entry.getExceptionClass() != null ? entry.getExceptionClass() : "").toLowerCase();
        String combined = msg + " " + ex;
        for (String kw : keywords) {
            if (combined.contains(kw))
                return true;
        }
        return false;
    }

    /**
     * Derives a recommended priority from the error type, capped by a maximum.
     */
    public static Priority recommendPriority(ErrorType type, Priority maxPriority) {
        Priority recommended = type.getDefaultPriority();
        if (maxPriority != null && recommended.ordinal() > maxPriority.ordinal()) {
            return maxPriority;
        }
        return recommended;
    }
}
