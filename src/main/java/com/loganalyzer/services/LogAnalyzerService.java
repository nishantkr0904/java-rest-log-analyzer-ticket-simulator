package com.loganalyzer.services;

import com.loganalyzer.models.ErrorType;
import com.loganalyzer.models.LogEntry;
import com.loganalyzer.utils.LogParser;
import com.loganalyzer.utils.RootCauseClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core service for parsing, analyzing, and summarizing REST API log files.
 * Detects HTTP 4xx/5xx errors and SQL/DB exceptions, classifies root causes,
 * and aggregates error frequency and timestamp-based groupings.
 */
public class LogAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(LogAnalyzerService.class);

    private final List<LogEntry> allEntries = new ArrayList<>();
    private final List<LogEntry> errorEntries = new ArrayList<>();
    private Path lastAnalyzedFile;

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Parses and analyzes the given log file.
     *
     * @param filePath path to the log file (JSON array or plain-text)
     * @throws IOException if the file cannot be read
     */
    public void analyze(Path filePath) throws IOException {
        logger.info("Starting log analysis: {}", filePath);
        allEntries.clear();
        errorEntries.clear();
        this.lastAnalyzedFile = filePath;

        List<LogEntry> parsed = LogParser.parse(filePath);
        allEntries.addAll(parsed);

        for (LogEntry entry : allEntries) {
            if (entry.isError()) {
                RootCauseClassifier.classify(entry);
                errorEntries.add(entry);
            }
        }
        logger.info("Analysis complete. Total: {}, Errors: {}", allEntries.size(), errorEntries.size());
    }

    /**
     * Returns all error entries detected in the last analyzed file.
     */
    public List<LogEntry> getErrorEntries() {
        return Collections.unmodifiableList(errorEntries);
    }

    /**
     * Returns all parsed log entries (errors and non-errors).
     */
    public List<LogEntry> getAllEntries() {
        return Collections.unmodifiableList(allEntries);
    }

    /**
     * Aggregates error frequency by {@link ErrorType}.
     *
     * @return map of ErrorType → count, sorted descending by count
     */
    public Map<ErrorType, Long> getErrorFrequency() {
        return errorEntries.stream()
                .filter(e -> e.getDetectedErrorType() != null)
                .collect(Collectors.groupingBy(LogEntry::getDetectedErrorType, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<ErrorType, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Groups errors by date (LocalDate) for timestamp-based analysis.
     *
     * @return map of date → list of error log entries
     */
    public Map<LocalDate, List<LogEntry>> getErrorsByDate() {
        return errorEntries.stream()
                .filter(e -> e.getParsedTimestamp() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getParsedTimestamp().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()));
    }

    /**
     * Groups errors by hour bucket for time-of-day analysis.
     *
     * @return map of hour (0-23) → error count
     */
    public Map<Integer, Long> getErrorsByHour() {
        return errorEntries.stream()
                .filter(e -> e.getParsedTimestamp() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getParsedTimestamp().getHour(),
                        TreeMap::new,
                        Collectors.counting()));
    }

    /**
     * Groups errors by affected endpoint.
     *
     * @return map of endpoint → error count, sorted descending
     */
    public Map<String, Long> getErrorsByEndpoint() {
        return errorEntries.stream()
                .filter(e -> e.getEndpoint() != null && !e.getEndpoint().isBlank())
                .collect(Collectors.groupingBy(LogEntry::getEndpoint, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Separates errors into HTTP client (4xx) vs server (5xx) vs database buckets.
     */
    public Map<String, List<LogEntry>> getErrorsByCategory() {
        Map<String, List<LogEntry>> categories = new LinkedHashMap<>();
        categories.put("HTTP Client Errors (4xx)", new ArrayList<>());
        categories.put("HTTP Server Errors (5xx)", new ArrayList<>());
        categories.put("Database / SQL Errors", new ArrayList<>());
        categories.put("Other Errors", new ArrayList<>());

        for (LogEntry entry : errorEntries) {
            if (entry.isHttpClientError()) {
                categories.get("HTTP Client Errors (4xx)").add(entry);
            } else if (entry.isHttpServerError()) {
                categories.get("HTTP Server Errors (5xx)").add(entry);
            } else if (entry.isDatabaseError()) {
                categories.get("Database / SQL Errors").add(entry);
            } else {
                categories.get("Other Errors").add(entry);
            }
        }
        return categories;
    }

    /**
     * Generates a comprehensive plain-text incident summary report.
     */
    public String generateSummaryReport() {
        if (allEntries.isEmpty())
            return "No log data loaded. Please analyze a log file first.";

        StringBuilder sb = new StringBuilder();
        String separator = "═".repeat(72);
        String thinLine = "─".repeat(72);

        sb.append("\n").append(separator).append("\n");
        sb.append("  INCIDENT ANALYSIS REPORT\n");
        sb.append("  Generated: ").append(LocalDateTime.now()
                .toString().replace("T", " ").substring(0, 19)).append("\n");
        if (lastAnalyzedFile != null)
            sb.append("  Source File: ").append(lastAnalyzedFile.getFileName()).append("\n");
        sb.append(separator).append("\n\n");

        // ── Overview ──
        long clientErrors = errorEntries.stream().filter(LogEntry::isHttpClientError).count();
        long serverErrors = errorEntries.stream().filter(LogEntry::isHttpServerError).count();
        long dbErrors = errorEntries.stream().filter(LogEntry::isDatabaseError).count();
        long otherErrors = errorEntries.size() - clientErrors - serverErrors - dbErrors;

        sb.append("  OVERVIEW\n").append(thinLine).append("\n");
        sb.append(String.format("  %-30s : %d%n", "Total Log Entries", allEntries.size()));
        sb.append(String.format("  %-30s : %d%n", "Total Errors Detected", errorEntries.size()));
        sb.append(String.format("  %-30s : %d%n", "HTTP Client Errors (4xx)", clientErrors));
        sb.append(String.format("  %-30s : %d%n", "HTTP Server Errors (5xx)", serverErrors));
        sb.append(String.format("  %-30s : %d%n", "Database / SQL Errors", dbErrors));
        sb.append(String.format("  %-30s : %d%n", "Other Errors", otherErrors));
        sb.append("\n");

        // ── Error Frequency by Type ──
        sb.append("  ERROR FREQUENCY BY TYPE\n").append(thinLine).append("\n");
        Map<ErrorType, Long> freq = getErrorFrequency();
        if (freq.isEmpty()) {
            sb.append("  No classified errors found.\n");
        } else {
            freq.forEach((type, count) -> sb.append(String.format("  %-40s : %3d occurrence(s)  [Root Cause: %s]%n",
                    type.getDisplayName(), count, type.getRootCauseDescription())));
        }
        sb.append("\n");

        // ── Errors by Date ──
        sb.append("  ERRORS BY DATE\n").append(thinLine).append("\n");
        Map<LocalDate, List<LogEntry>> byDate = getErrorsByDate();
        if (byDate.isEmpty()) {
            sb.append("  No timestamped errors found.\n");
        } else {
            byDate.forEach((date, entries) -> sb.append(String.format("  %s : %d error(s)%n", date, entries.size())));
        }
        sb.append("\n");

        // ── Top Affected Endpoints ──
        Map<String, Long> byEndpoint = getErrorsByEndpoint();
        if (!byEndpoint.isEmpty()) {
            sb.append("  TOP AFFECTED ENDPOINTS\n").append(thinLine).append("\n");
            byEndpoint.entrySet().stream().limit(5)
                    .forEach(e -> sb.append(String.format("  %-40s : %d error(s)%n", e.getKey(), e.getValue())));
            sb.append("\n");
        }

        // ── Root Cause Summary ──
        sb.append("  ROOT CAUSE CLASSIFICATION SUMMARY\n").append(thinLine).append("\n");
        if (clientErrors > 0)
            sb.append(
                    "  ► 4xx errors indicate client-side issues: bad requests, missing auth, or invalid resources.\n");
        if (serverErrors > 0)
            sb.append("  ► 5xx errors indicate server-side failures requiring immediate investigation.\n");
        if (dbErrors > 0)
            sb.append("  ► Database errors detected — check connection pools, credentials, and query syntax.\n");

        long criticalCount = errorEntries.stream()
                .filter(e -> e.getDetectedErrorType() != null &&
                        e.getDetectedErrorType().getDefaultPriority().name().equals("CRITICAL"))
                .count();
        if (criticalCount > 0)
            sb.append(String.format("  ► %d CRITICAL error(s) detected — auto-ticket generation recommended.%n",
                    criticalCount));

        sb.append("\n").append(separator).append("\n");
        return sb.toString();
    }

    public Path getLastAnalyzedFile() {
        return lastAnalyzedFile;
    }

    public boolean hasData() {
        return !allEntries.isEmpty();
    }

    public int getTotalErrors() {
        return errorEntries.size();
    }

    public int getTotalEntries() {
        return allEntries.size();
    }
}
