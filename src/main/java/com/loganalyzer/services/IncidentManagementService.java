package com.loganalyzer.services;

import com.loganalyzer.models.*;
import com.loganalyzer.sla.SlaComplianceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the end-to-end incident management workflow:
 * <ol>
 * <li>Ingest error entries from {@link LogAnalyzerService}</li>
 * <li>Auto-generate tickets via {@link TicketService}</li>
 * <li>Evaluate SLA compliance via {@link SlaComplianceEngine}</li>
 * <li>Produce a unified incident summary report</li>
 * </ol>
 */
public class IncidentManagementService {

    private static final Logger logger = LoggerFactory.getLogger(IncidentManagementService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogAnalyzerService logAnalyzer;
    private final TicketService ticketService;
    private final SlaComplianceEngine slaEngine;

    public IncidentManagementService(LogAnalyzerService logAnalyzer,
            TicketService ticketService,
            SlaComplianceEngine slaEngine) {
        this.logAnalyzer = logAnalyzer;
        this.ticketService = ticketService;
        this.slaEngine = slaEngine;
    }

    /**
     * Auto-generates tickets from all error entries detected in the last log
     * analysis.
     * Deduplicates by error type — one ticket per unique error type.
     *
     * @return list of newly created tickets
     */
    public List<Ticket> generateTicketsFromLogs() {
        if (!logAnalyzer.hasData()) {
            logger.warn("No log data loaded — run log analysis first");
            return Collections.emptyList();
        }

        List<LogEntry> errors = logAnalyzer.getErrorEntries();
        logger.info("Auto-generating tickets from {} error entries", errors.size());

        // Group by ErrorType to avoid duplicate tickets for the same error kind
        Map<ErrorType, List<LogEntry>> grouped = errors.stream()
                .filter(e -> e.getDetectedErrorType() != null)
                .collect(
                        Collectors.groupingBy(LogEntry::getDetectedErrorType, LinkedHashMap::new, Collectors.toList()));

        List<Ticket> created = new ArrayList<>();
        for (Map.Entry<ErrorType, List<LogEntry>> entry : grouped.entrySet()) {
            LogEntry representative = pickRepresentative(entry.getValue());
            Ticket ticket = ticketService.createFromLogEntry(representative);
            // Enhance title to show occurrence count when > 1
            if (entry.getValue().size() > 1) {
                ticket.setTitle(ticket.getTitle() + String.format(" (%d occurrences)", entry.getValue().size()));
            }
            created.add(ticket);
            slaEngine.alertIfBreached(ticket);
        }

        logger.info("Auto-generated {} tickets from log analysis", created.size());
        return created;
    }

    /**
     * Generates a unified incident summary report combining log analysis,
     * ticket status, and SLA compliance metrics.
     */
    public String generateIncidentSummaryReport() {
        StringBuilder sb = new StringBuilder();
        String sep = "═".repeat(72);
        String thin = "─".repeat(72);

        sb.append("\n").append(sep).append("\n");
        sb.append("  UNIFIED INCIDENT SUMMARY REPORT\n");
        sb.append("  Generated: ").append(LocalDateTime.now().format(FMT)).append("\n");
        sb.append(sep).append("\n\n");

        // ── Log Analysis Summary ──
        if (logAnalyzer.hasData()) {
            sb.append("  LOG ANALYSIS OVERVIEW\n").append(thin).append("\n");
            sb.append(String.format("  %-30s : %d%n", "Total Log Entries", logAnalyzer.getTotalEntries()));
            sb.append(String.format("  %-30s : %d%n", "Total Errors Detected", logAnalyzer.getTotalErrors()));

            Map<ErrorType, Long> freq = logAnalyzer.getErrorFrequency();
            if (!freq.isEmpty()) {
                sb.append("\n  Top Error Types:\n");
                freq.entrySet().stream().limit(5)
                        .forEach(e -> sb.append(String.format("    • %-40s : %d occurrence(s)%n",
                                e.getKey().getDisplayName(), e.getValue())));
            }
            sb.append("\n");
        }

        // ── Ticket Summary ──
        List<Ticket> allTickets = ticketService.getAllTickets();
        sb.append("  TICKET STATUS SUMMARY\n").append(thin).append("\n");
        if (allTickets.isEmpty()) {
            sb.append("  No tickets exist. Run auto-generate from logs to create tickets.\n");
        } else {
            sb.append(String.format("  %-30s : %d%n", "Total Tickets", allTickets.size()));
            for (TicketStatus status : TicketStatus.values()) {
                long count = allTickets.stream().filter(t -> t.getStatus() == status).count();
                if (count > 0)
                    sb.append(String.format("  %-30s : %d%n", status.getDisplayName(), count));
            }
            sb.append("\n");

            // Priority breakdown
            sb.append("  TICKETS BY PRIORITY\n").append(thin).append("\n");
            for (Priority p : Priority.values()) {
                long count = allTickets.stream().filter(t -> t.getPriority() == p).count();
                if (count > 0)
                    sb.append(String.format("  %-10s : %d ticket(s) [SLA: %dh]%n",
                            p.getDisplayName(), count, p.getSlaHours()));
            }
            sb.append("\n");

            // Ticket table
            sb.append("  TICKET LIST\n").append(thin).append("\n");
            sb.append(String.format("  %-12s | %-8s | %-10s | %-20s | %-14s | %s%n",
                    "Ticket ID", "Priority", "Status", "Issue Type", "SLA Status", "Created At"));
            sb.append("  " + "─".repeat(90)).append("\n");
            allTickets.forEach(t -> sb.append("  ").append(t.getSummaryLine()).append("\n"));
            sb.append("\n");
        }

        // ── SLA Compliance ──
        if (!allTickets.isEmpty()) {
            SlaComplianceEngine.SlaReport slaReport = slaEngine.evaluate(allTickets);
            sb.append("  SLA COMPLIANCE\n").append(thin).append("\n");
            sb.append(String.format("  %-30s : %.1f%%%n", "Overall Compliance Rate", slaReport.complianceRate));
            sb.append(String.format("  %-30s : %.0f min%n", "Avg Time to Resolution", slaReport.avgResolutionMinutes));
            sb.append(String.format("  %-30s : %d%n", "SLA Breached Tickets", slaReport.breachedTickets));
            if (!slaReport.criticalBreaches.isEmpty()) {
                sb.append("\n  ⚠ Critical Breaches:\n");
                slaReport.criticalBreaches.forEach(t -> sb.append(String.format("    • %s breached by %d min%n",
                        t.getTicketId(), t.getSlaBreachMinutes())));
            }
            sb.append("\n");
        }

        sb.append("  ROOT CAUSE MAPPING\n").append(thin).append("\n");
        sb.append("  Error Type             → Root Cause               → Ticket Priority\n");
        sb.append("  " + "─".repeat(70)).append("\n");
        if (!ticketService.isEmpty()) {
            ticketService.getAllTickets().stream()
                    .filter(Ticket::isAutoGenerated)
                    .forEach(t -> sb.append(String.format(
                            "  %-22s → %-25s → %s%n",
                            truncate(t.getIssueType().getDisplayName(), 22),
                            truncate(t.getIssueType().getRootCauseDescription(), 25),
                            t.getPriority().getDisplayName())));
        } else {
            sb.append("  No auto-generated tickets yet.\n");
        }

        sb.append("\n").append(sep).append("\n");
        return sb.toString();
    }

    private LogEntry pickRepresentative(List<LogEntry> entries) {
        // Prefer entries with status codes; otherwise take the first
        return entries.stream()
                .filter(e -> e.getStatusCode() != null)
                .findFirst()
                .orElse(entries.get(0));
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
