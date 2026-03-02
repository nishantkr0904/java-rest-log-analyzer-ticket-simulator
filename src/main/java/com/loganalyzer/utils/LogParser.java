package com.loganalyzer.utils;

import com.loganalyzer.models.LogEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses log files in JSON array format or plain-text log format into
 * {@link LogEntry} objects.
 * Supports auto-detection of file format based on content.
 */
public class LogParser {

    private static final Logger logger = LoggerFactory.getLogger(LogParser.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Plain-text log pattern: [2024-01-15 14:23:55] ERROR GET /api/users 500 -
    // message
    private static final Pattern PLAIN_LOG_PATTERN = Pattern.compile(
            "^\\[?(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)]?" +
                    "\\s+(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\s+" +
                    "(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)?\\s*" +
                    "([/\\w.\\-{}?=&]+)?\\s*" +
                    "(\\d{3})?\\s*[-–]?\\s*(.+)?$",
            Pattern.CASE_INSENSITIVE);

    // Simpler fallback for ERROR/FATAL lines
    private static final Pattern ERROR_LINE_PATTERN = Pattern.compile(
            "(\\d{4}[-/]\\d{2}[-/]\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}).*?(ERROR|FATAL|WARN).*?(\\d{3})?\\s[-–]\\s(.+)",
            Pattern.CASE_INSENSITIVE);

    private static final List<DateTimeFormatter> TIMESTAMP_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));

    private LogParser() {
    }

    /**
     * Parses the given file, auto-detecting JSON vs plain-text format.
     *
     * @param filePath path to the log file
     * @return list of parsed LogEntry objects
     * @throws IOException if the file cannot be read
     */
    public static List<LogEntry> parse(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("Log file not found: " + filePath);
        }
        String content = Files.readString(filePath).trim();
        logger.info("Parsing log file: {} ({} bytes)", filePath.getFileName(), content.length());

        if (content.startsWith("[") || content.startsWith("{")) {
            return parseJson(content, filePath);
        } else {
            return parsePlainText(content);
        }
    }

    // ─── JSON Parsing ──────────────────────────────────────────────────────────

    private static List<LogEntry> parseJson(String content, Path filePath) throws IOException {
        List<LogEntry> entries;
        try {
            if (content.startsWith("[")) {
                entries = MAPPER.readValue(content, new TypeReference<List<LogEntry>>() {
                });
            } else {
                // Single object
                entries = new ArrayList<>();
                entries.add(MAPPER.readValue(content, LogEntry.class));
            }
        } catch (Exception e) {
            logger.warn("JSON parse failed for {}, falling back to plain-text: {}", filePath, e.getMessage());
            return parsePlainText(content);
        }

        // Resolve parsed timestamps
        for (LogEntry entry : entries) {
            if (entry.getRawTimestamp() != null && entry.getParsedTimestamp() == null) {
                entry.setParsedTimestamp(parseTimestamp(entry.getRawTimestamp()));
            }
        }
        logger.info("Parsed {} JSON log entries", entries.size());
        return entries;
    }

    // ─── Plain-Text Parsing ────────────────────────────────────────────────────

    private static List<LogEntry> parsePlainText(String content) {
        List<LogEntry> entries = new ArrayList<>();
        String[] lines = content.split("\n");

        for (String rawLine : lines) {
            rawLine = rawLine.trim();
            if (rawLine.isEmpty())
                continue;

            LogEntry entry = tryParsePlainLine(rawLine);
            if (entry != null) {
                entries.add(entry);
            }
        }
        logger.info("Parsed {} plain-text log entries", entries.size());
        return entries;
    }

    private static LogEntry tryParsePlainLine(String rawLine) {
        Matcher m = PLAIN_LOG_PATTERN.matcher(rawLine);
        if (m.matches()) {
            String ts = m.group(1);
            String level = m.group(2);
            String method = m.group(3);
            String endpoint = m.group(4);
            String statusStr = m.group(5);
            String message = m.group(6);

            Integer statusCode = null;
            if (statusStr != null && !statusStr.isBlank()) {
                try {
                    statusCode = Integer.parseInt(statusStr.trim());
                } catch (NumberFormatException ignored) {
                }
            }

            LogEntry entry = new LogEntry(rawLine, parseTimestamp(ts), level,
                    message != null ? message.trim() : rawLine, statusCode);
            entry.setHttpMethod(method);
            entry.setEndpoint(endpoint);
            entry.setRawTimestamp(ts);

            // Extract exception hints from message
            if (message != null) {
                if (message.contains("Exception") || message.contains("Error")) {
                    // Pull first word that looks like a class name
                    Pattern exPat = Pattern.compile("([A-Za-z]+(?:Exception|Error))");
                    Matcher ex = exPat.matcher(message);
                    if (ex.find())
                        entry.setExceptionClass(ex.group(1));
                }
            }
            return entry;
        }

        // Fallback: any line with ERROR/FATAL and a message
        String lower = rawLine.toLowerCase();
        if (lower.contains("error") || lower.contains("fatal") || lower.contains("exception")) {
            LogEntry entry = new LogEntry();
            entry.setRawLine(rawLine);
            entry.setMessage(rawLine);
            entry.setParsedTimestamp(LocalDateTime.now());

            if (lower.contains("fatal"))
                entry.setLogLevel("FATAL");
            else
                entry.setLogLevel("ERROR");

            // Try to find HTTP status codes
            Pattern codePat = Pattern.compile("\\b([45]\\d{2})\\b");
            Matcher cm = codePat.matcher(rawLine);
            if (cm.find()) {
                try {
                    entry.setStatusCode(Integer.parseInt(cm.group(1)));
                } catch (NumberFormatException ignored) {
                }
            }
            return entry;
        }
        return null;
    }

    // ─── Timestamp Parsing ─────────────────────────────────────────────────────

    public static LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank())
            return LocalDateTime.now();
        String cleaned = raw.replace("T", " ").replaceAll("Z$", "");
        for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
            try {
                return LocalDateTime.parse(cleaned, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        // Try ISO with T separator
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
        }
        logger.warn("Could not parse timestamp: {}", raw);
        return LocalDateTime.now();
    }
}
