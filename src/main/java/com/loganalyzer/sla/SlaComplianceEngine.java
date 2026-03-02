package com.loganalyzer.sla;

import com.loganalyzer.models.Priority;
import com.loganalyzer.models.Ticket;
import com.loganalyzer.models.TicketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SLA Compliance Engine that evaluates ticket lifecycles against their SLA
 * deadlines.
 * Computes breach rates, time-to-resolution statistics, and compliance metrics
 * broken down by priority level.
 */
public class SlaComplianceEngine {

    private static final Logger logger = LoggerFactory.getLogger(SlaComplianceEngine.class);

    /**
     * Encapsulates SLA compliance results for a list of tickets.
     */
    public static class SlaReport {
        public final int totalTickets;
        public final int breachedTickets;
        public final int withinSlaTickets;
        public final double complianceRate; // percentage (0-100)
        public final double avgResolutionMinutes;
        public final Map<Priority, PrioritySlaStats> statsByPriority;
        public final List<Ticket> criticalBreaches; // CRITICAL tickets that breached SLA
        public final LocalDateTime generatedAt;

        public SlaReport(int total, int breached, double avgRes,
                Map<Priority, PrioritySlaStats> statsByPriority,
                List<Ticket> criticalBreaches) {
            this.totalTickets = total;
            this.breachedTickets = breached;
            this.withinSlaTickets = total - breached;
            this.complianceRate = total > 0 ? ((double) (total - breached) / total) * 100.0 : 100.0;
            this.avgResolutionMinutes = avgRes;
            this.statsByPriority = statsByPriority;
            this.criticalBreaches = criticalBreaches;
            this.generatedAt = LocalDateTime.now();
        }
    }

    /**
     * Per-priority SLA statistics.
     */
    public static class PrioritySlaStats {
        public final Priority priority;
        public final int total;
        public final int breached;
        public final double complianceRate;
        public final double avgResolutionMinutes;
        public final long maxResolutionMinutes;

        public PrioritySlaStats(Priority priority, List<Ticket> tickets) {
            this.priority = priority;
            this.total = tickets.size();
            this.breached = (int) tickets.stream().filter(Ticket::isSlaBreached).count();
            this.complianceRate = total > 0 ? ((double) (total - breached) / total) * 100.0 : 100.0;

            List<Long> resolutionTimes = tickets.stream()
                    .filter(t -> t.getTimeToResolutionMinutes() >= 0)
                    .map(Ticket::getTimeToResolutionMinutes)
                    .collect(Collectors.toList());

            this.avgResolutionMinutes = resolutionTimes.stream()
                    .mapToLong(Long::longValue).average().orElse(0.0);
            this.maxResolutionMinutes = resolutionTimes.stream()
                    .mapToLong(Long::longValue).max().orElse(0L);
        }
    }

    /**
     * Evaluates SLA compliance across all provided tickets.
     * Tickets that are unresolved count against SLA if their deadline has passed.
     *
     * @param tickets list of tickets to evaluate
     * @return {@link SlaReport} containing full compliance metrics
     */
    public SlaReport evaluate(List<Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            logger.warn("No tickets provided for SLA evaluation");
            return new SlaReport(0, 0, 0.0, new EnumMap<>(Priority.class), new ArrayList<>());
        }

        logger.info("Evaluating SLA compliance for {} tickets", tickets.size());

        int totalBreached = (int) tickets.stream().filter(Ticket::isSlaBreached).count();

        double avgResolution = tickets.stream()
                .filter(t -> t.getTimeToResolutionMinutes() >= 0)
                .mapToLong(Ticket::getTimeToResolutionMinutes)
                .average().orElse(0.0);

        // Per-priority stats
        Map<Priority, PrioritySlaStats> byPriority = new EnumMap<>(Priority.class);
        for (Priority p : Priority.values()) {
            List<Ticket> byP = tickets.stream()
                    .filter(t -> t.getPriority() == p)
                    .collect(Collectors.toList());
            if (!byP.isEmpty()) {
                byPriority.put(p, new PrioritySlaStats(p, byP));
            }
        }

        List<Ticket> criticalBreaches = tickets.stream()
                .filter(t -> t.getPriority() == Priority.CRITICAL && t.isSlaBreached())
                .collect(Collectors.toList());

        SlaReport report = new SlaReport(tickets.size(), totalBreached, avgResolution,
                byPriority, criticalBreaches);

        logger.info("SLA Compliance: {}/{} within SLA ({:.1f}%)",
                report.withinSlaTickets, tickets.size(), report.complianceRate);
        return report;
    }

    /**
     * Generates a formatted SLA compliance report string.
     */
    public String formatReport(SlaReport report) {
        StringBuilder sb = new StringBuilder();
        String sep = "═".repeat(72);
        String thin = "─".repeat(72);

        sb.append("\n").append(sep).append("\n");
        sb.append("  SLA COMPLIANCE REPORT\n");
        sb.append("  Generated: ").append(report.generatedAt.toString()
                .replace("T", " ").substring(0, 19)).append("\n");
        sb.append(sep).append("\n\n");

        sb.append("  SUMMARY\n").append(thin).append("\n");
        sb.append(String.format("  %-35s : %d%n", "Total Tickets Evaluated", report.totalTickets));
        sb.append(String.format("  %-35s : %d%n", "Tickets Within SLA", report.withinSlaTickets));
        sb.append(String.format("  %-35s : %d%n", "Tickets Breached SLA", report.breachedTickets));
        sb.append(String.format("  %-35s : %.1f%%%n", "Overall Compliance Rate", report.complianceRate));
        sb.append(String.format("  %-35s : %.0f min%n", "Avg Time to Resolution", report.avgResolutionMinutes));
        sb.append("\n");

        if (!report.statsByPriority.isEmpty()) {
            sb.append("  COMPLIANCE BY PRIORITY\n").append(thin).append("\n");
            sb.append(String.format("  %-10s | %-6s | %-8s | %-10s | %-14s | %-12s%n",
                    "Priority", "Total", "Breached", "SLA Hours", "Compliance %", "Avg Res(min)"));
            sb.append("  " + "─".repeat(68)).append("\n");

            for (Priority p : Priority.values()) {
                PrioritySlaStats s = report.statsByPriority.get(p);
                if (s != null) {
                    sb.append(String.format("  %-10s | %-6d | %-8d | %-10d | %-14.1f | %-12.0f%n",
                            p.getDisplayName(), s.total, s.breached, p.getSlaHours(),
                            s.complianceRate, s.avgResolutionMinutes));
                }
            }
            sb.append("\n");
        }

        // SLA Compliance visual bar
        sb.append("  SLA COMPLIANCE GAUGE\n").append(thin).append("\n");
        int bars = (int) (report.complianceRate / 5);
        String complianceColor = report.complianceRate >= 90 ? "✓" : report.complianceRate >= 70 ? "⚡" : "⚠";
        sb.append(String.format("  %s [%s%s] %.1f%%%n",
                complianceColor,
                "█".repeat(bars),
                "░".repeat(20 - bars),
                report.complianceRate));
        sb.append("\n");

        if (!report.criticalBreaches.isEmpty()) {
            sb.append("  ⚠ CRITICAL SLA BREACHES\n").append(thin).append("\n");
            report.criticalBreaches.forEach(t -> sb.append(String.format("  • %s — %s (breached by %d min)%n",
                    t.getTicketId(), t.getTitle(), t.getSlaBreachMinutes())));
            sb.append("\n");
        }

        sb.append("  SLA DEFINITION TABLE\n").append(thin).append("\n");
        for (Priority p : Priority.values()) {
            sb.append(String.format("  %-10s : Resolve within %-4d hours — %s%n",
                    p.getDisplayName(), p.getSlaHours(), p.getDescription()));
        }

        sb.append("\n").append(sep).append("\n");
        return sb.toString();
    }

    /**
     * Checks a single ticket for SLA breach and logs a warning if breached.
     */
    public void alertIfBreached(Ticket ticket) {
        if (ticket.isSlaBreached() && !ticket.getStatus().isTerminal()) {
            logger.warn("SLA BREACH ALERT: Ticket {} [{}] exceeded deadline by {} minutes",
                    ticket.getTicketId(), ticket.getPriority(), ticket.getSlaBreachMinutes());
        }
    }
}
