package com.loganalyzer.cli;

import com.loganalyzer.models.*;
import com.loganalyzer.services.*;
import com.loganalyzer.sla.SlaComplianceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Main CLI entry point for the Java REST API Log Analyzer &amp; SLA Ticket
 * Simulator.
 *
 * <p>
 * Provides a menu-driven interactive interface to:
 * <ul>
 * <li>Analyze log files for HTTP and DB errors</li>
 * <li>Generate incident summary reports</li>
 * <li>Manage ticket lifecycle</li>
 * <li>Run SLA compliance evaluation</li>
 * </ul>
 */
public class MainCLI {

    private static final Logger logger = LoggerFactory.getLogger(MainCLI.class);

    // Services
    private final LogAnalyzerService logAnalyzer;
    private final TicketService ticketService;
    private final SlaComplianceEngine slaEngine;
    private final IncidentManagementService incidentService;
    private final Scanner scanner;

    public MainCLI() {
        this.logAnalyzer = new LogAnalyzerService();
        this.ticketService = new TicketService();
        this.slaEngine = new SlaComplianceEngine();
        this.incidentService = new IncidentManagementService(logAnalyzer, ticketService, slaEngine);
        this.scanner = new Scanner(System.in);
    }

    // ─── Main ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        printBanner();
        MainCLI cli = new MainCLI();
        cli.run();
    }

    private void run() {
        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = prompt("Enter choice");
            running = handleMainMenu(choice);
        }
        System.out.println("\n  Goodbye! Exiting application.\n");
        scanner.close();
    }

    // ─── Main Menu ────────────────────────────────────────────────────────────

    private void printMainMenu() {
        System.out.println("\n" + "═".repeat(60));
        System.out.println("  MAIN MENU");
        System.out.println("═".repeat(60));
        System.out.println("  [1] Log Analyzer");
        System.out.println("  [2] Ticket Management");
        System.out.println("  [3] SLA Compliance");
        System.out.println("  [4] Incident Management");
        System.out.println("  [0] Exit");
        System.out.println("═".repeat(60));
    }

    private boolean handleMainMenu(String choice) {
        return switch (choice.trim()) {
            case "1" -> {
                showLogAnalyzerMenu();
                yield true;
            }
            case "2" -> {
                showTicketMenu();
                yield true;
            }
            case "3" -> {
                showSlaMenu();
                yield true;
            }
            case "4" -> {
                showIncidentMenu();
                yield true;
            }
            case "0" -> false;
            default -> {
                printError("Invalid choice. Please try again.");
                yield true;
            }
        };
    }

    // ─── Log Analyzer Menu ────────────────────────────────────────────────────

    private void showLogAnalyzerMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n" + "─".repeat(60));
            System.out.println("  LOG ANALYZER");
            System.out.println("─".repeat(60));
            System.out.println("  [1] Analyze log file");
            System.out.println("  [2] Show error summary");
            System.out.println("  [3] Show errors by category");
            System.out.println("  [4] Show error frequency by type");
            System.out.println("  [5] Show errors by date");
            System.out.println("  [6] Show top affected endpoints");
            System.out.println("  [7] Generate full incident report");
            System.out.println("  [0] Back");
            System.out.println("─".repeat(60));

            switch (prompt("Enter choice").trim()) {
                case "1" -> analyzeLogFile();
                case "2" -> showErrorSummary();
                case "3" -> showErrorsByCategory();
                case "4" -> showErrorFrequency();
                case "5" -> showErrorsByDate();
                case "6" -> showErrorsByEndpoint();
                case "7" -> System.out.println(logAnalyzer.generateSummaryReport());
                case "0" -> back = true;
                default -> printError("Invalid choice.");
            }
        }
    }

    private void analyzeLogFile() {
        System.out.println("\n  Available sample log files:");
        System.out.println("  sample_logs/api_logs.json");
        System.out.println("  sample_logs/application.log");
        System.out.println("  sample_logs/db_errors.log");
        System.out.println("  sample_logs/mixed_logs.json");

        String input = prompt("Enter log file path (or press Enter for default sample_logs/api_logs.json)");
        if (input.isBlank())
            input = "sample_logs/api_logs.json";

        Path path = Paths.get(input.trim());
        if (!Files.exists(path)) {
            // Try relative to working dir
            path = Paths.get(System.getProperty("user.dir"), input.trim());
        }

        try {
            System.out.println("\n  Analyzing: " + path + " ...");
            logAnalyzer.analyze(path);
            printSuccess(String.format("Analysis complete! Found %d entries, %d errors.",
                    logAnalyzer.getTotalEntries(), logAnalyzer.getTotalErrors()));
        } catch (IOException e) {
            printError("Failed to read log file: " + e.getMessage());
            logger.error("Log analysis failed for path: {}", path, e);
        }
    }

    private void showErrorSummary() {
        if (!logAnalyzer.hasData()) {
            printError("No data. Please analyze a log file first.");
            return;
        }
        System.out.printf("%n  Total entries: %d | Errors: %d%n",
                logAnalyzer.getTotalEntries(), logAnalyzer.getTotalErrors());
    }

    private void showErrorsByCategory() {
        if (!logAnalyzer.hasData()) {
            printError("No data. Please analyze a log file first.");
            return;
        }
        logAnalyzer.getErrorsByCategory().forEach((cat, entries) -> {
            if (!entries.isEmpty()) {
                System.out.printf("%n  %s (%d):%n", cat, entries.size());
                entries.stream().limit(5).forEach(e -> System.out.printf("    [%s] %s HTTP-%s %s%n",
                        e.getFormattedTimestamp(), e.getHttpMethod() != null ? e.getHttpMethod() : "",
                        e.getStatusCode() != null ? e.getStatusCode() : "?",
                        e.getMessage() != null ? truncate(e.getMessage(), 60) : ""));
                if (entries.size() > 5)
                    System.out.printf("    ... and %d more%n", entries.size() - 5);
            }
        });
    }

    private void showErrorFrequency() {
        if (!logAnalyzer.hasData()) {
            printError("No data. Please analyze a log file first.");
            return;
        }
        System.out.println("\n  Error Frequency by Type:");
        System.out.println("  " + "─".repeat(60));
        logAnalyzer.getErrorFrequency()
                .forEach((type, count) -> System.out.printf("  %-40s : %3d%n", type.getDisplayName(), count));
    }

    private void showErrorsByDate() {
        if (!logAnalyzer.hasData()) {
            printError("No data. Please analyze a log file first.");
            return;
        }
        System.out.println("\n  Errors by Date:");
        logAnalyzer.getErrorsByDate()
                .forEach((date, entries) -> System.out.printf("  %s : %d error(s)%n", date, entries.size()));
    }

    private void showErrorsByEndpoint() {
        if (!logAnalyzer.hasData()) {
            printError("No data. Please analyze a log file first.");
            return;
        }
        System.out.println("\n  Top Affected Endpoints:");
        logAnalyzer.getErrorsByEndpoint().entrySet().stream().limit(10)
                .forEach(e -> System.out.printf("  %-40s : %d%n", e.getKey(), e.getValue()));
    }

    // ─── Ticket Menu ──────────────────────────────────────────────────────────

    private void showTicketMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n" + "─".repeat(60));
            System.out.println("  TICKET MANAGEMENT");
            System.out.println("─".repeat(60));
            System.out.println("  [1] Create new ticket manually");
            System.out.println("  [2] List all tickets");
            System.out.println("  [3] View ticket details");
            System.out.println("  [4] Update ticket status");
            System.out.println("  [5] Update ticket priority");
            System.out.println("  [6] Assign ticket to engineer");
            System.out.println("  [7] Resolve ticket");
            System.out.println("  [8] Close ticket");
            System.out.println("  [9] List open critical tickets");
            System.out.println("  [0] Back");
            System.out.println("─".repeat(60));

            switch (prompt("Enter choice").trim()) {
                case "1" -> createTicketManually();
                case "2" -> listAllTickets();
                case "3" -> viewTicketDetails();
                case "4" -> updateTicketStatus();
                case "5" -> updateTicketPriority();
                case "6" -> assignTicket();
                case "7" -> resolveTicket();
                case "8" -> closeTicket();
                case "9" -> listCriticalTickets();
                case "0" -> back = true;
                default -> printError("Invalid choice.");
            }
        }
    }

    private void createTicketManually() {
        System.out.println("\n  CREATE TICKET");
        String title = prompt("  Title");
        String description = prompt("  Description");

        System.out.println("  Issue Types:");
        ErrorType[] types = ErrorType.values();
        for (int i = 0; i < types.length; i++) {
            System.out.printf("    [%2d] %s%n", i + 1, types[i].getDisplayName());
        }
        int typeIdx = readInt("  Select issue type number", 1, types.length) - 1;

        System.out.println("  Priority: [1] Low  [2] Medium  [3] High  [4] Critical");
        int priIdx = readInt("  Select priority", 1, 4) - 1;
        Priority priority = Priority.values()[priIdx];

        Ticket t = ticketService.createTicket(title, description, types[typeIdx], priority);
        printSuccess("Ticket created: " + t.getTicketId());
        System.out.println(t.getDetailedView());
    }

    private void listAllTickets() {
        List<Ticket> tickets = ticketService.getAllTickets();
        if (tickets.isEmpty()) {
            System.out.println("\n  No tickets found.");
            return;
        }
        System.out.println("\n  ALL TICKETS");
        System.out.println("  " + "─".repeat(90));
        System.out.printf("  %-12s | %-8s | %-10s | %-16s | %-14s | %s%n",
                "Ticket ID", "Priority", "Status", "Issue Type", "SLA Status", "Created At");
        System.out.println("  " + "─".repeat(90));
        tickets.forEach(t -> System.out.println("  " + t.getSummaryLine()));
    }

    private void viewTicketDetails() {
        String id = prompt("  Ticket ID").trim().toUpperCase();
        ticketService.findById(id).ifPresentOrElse(
                t -> System.out.println(t.getDetailedView()),
                () -> printError("Ticket not found: " + id));
    }

    private void updateTicketStatus() {
        String id = prompt("  Ticket ID").trim().toUpperCase();
        ticketService.findById(id).ifPresentOrElse(t -> {
            System.out.println("  Current status: " + t.getStatus());
            System.out.println("  Status options:");
            TicketStatus[] statuses = TicketStatus.values();
            for (int i = 0; i < statuses.length; i++) {
                System.out.printf("    [%d] %s%n", i + 1, statuses[i].getDisplayName());
            }
            int idx = readInt("  Select new status", 1, statuses.length) - 1;
            try {
                ticketService.updateStatus(id, statuses[idx]);
                printSuccess("Status updated to: " + statuses[idx]);
            } catch (IllegalStateException e) {
                printError("Invalid transition: " + e.getMessage());
            }
        }, () -> printError("Ticket not found: " + id));
    }

    private void updateTicketPriority() {
        String id = prompt("  Ticket ID").trim().toUpperCase();
        System.out.println("  Priority: [1] Low  [2] Medium  [3] High  [4] Critical");
        int idx = readInt("  Select new priority", 1, 4) - 1;
        try {
            ticketService.updatePriority(id, Priority.values()[idx]);
            printSuccess("Priority updated.");
        } catch (Exception e) {
            printError(e.getMessage());
        }
    }

    private void assignTicket() {
        String id = prompt("  Ticket ID").trim().toUpperCase();
        String assignee = prompt("  Assignee name");
        try {
            ticketService.assignTicket(id, assignee);
            printSuccess("Ticket " + id + " assigned to " + assignee);
        } catch (Exception e) {
            printError(e.getMessage());
        }
    }

    private void resolveTicket() {
        String id = prompt("  Ticket ID").trim().toUpperCase();
        String notes = prompt("  Resolution notes");
        try {
            ticketService.resolveTicket(id, notes);
            printSuccess("Ticket " + id + " resolved.");
            ticketService.findById(id).ifPresent(t -> System.out.printf("  SLA status: %s%n",
                    t.isSlaBreached() ? "⚠ BREACHED by " + t.getSlaBreachMinutes() + " min" : "✓ Within SLA"));
        } catch (Exception e) {
            printError(e.getMessage());
        }
    }

    private void closeTicket() {
        String id = prompt("  Ticket ID").trim().toUpperCase();
        try {
            ticketService.closeTicket(id);
            printSuccess("Ticket " + id + " closed.");
        } catch (Exception e) {
            printError(e.getMessage());
        }
    }

    private void listCriticalTickets() {
        List<Ticket> critical = ticketService.getOpenCriticalTickets();
        if (critical.isEmpty()) {
            System.out.println("\n  No open critical tickets.");
            return;
        }
        System.out.println("\n  OPEN CRITICAL TICKETS:");
        critical.forEach(t -> System.out.println("  " + t.getSummaryLine()));
    }

    // ─── SLA Menu ─────────────────────────────────────────────────────────────

    private void showSlaMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n" + "─".repeat(60));
            System.out.println("  SLA COMPLIANCE ENGINE");
            System.out.println("─".repeat(60));
            System.out.println("  [1] Run full SLA evaluation");
            System.out.println("  [2] Show SLA-breached tickets");
            System.out.println("  [3] Show SLA definitions");
            System.out.println("  [0] Back");
            System.out.println("─".repeat(60));

            switch (prompt("Enter choice").trim()) {
                case "1" -> runSlaEvaluation();
                case "2" -> showBreachedTickets();
                case "3" -> showSlaDefinitions();
                case "0" -> back = true;
                default -> printError("Invalid choice.");
            }
        }
    }

    private void runSlaEvaluation() {
        List<Ticket> tickets = ticketService.getAllTickets();
        if (tickets.isEmpty()) {
            printError("No tickets to evaluate.");
            return;
        }
        SlaComplianceEngine.SlaReport report = slaEngine.evaluate(tickets);
        System.out.println(slaEngine.formatReport(report));
    }

    private void showBreachedTickets() {
        List<Ticket> breached = ticketService.getSlaBreachedTickets();
        if (breached.isEmpty()) {
            System.out.println("\n  ✓ No SLA breaches detected.");
            return;
        }
        System.out.println("\n  ⚠ SLA-BREACHED TICKETS:");
        breached.forEach(t -> System.out.printf(
                "  %s | Priority: %-8s | Breached by: %dmin | Status: %s%n",
                t.getTicketId(), t.getPriority(), t.getSlaBreachMinutes(), t.getStatus()));
    }

    private void showSlaDefinitions() {
        System.out.println("\n  SLA DEFINITIONS");
        System.out.println("  " + "─".repeat(60));
        for (Priority p : Priority.values()) {
            System.out.printf("  %-10s : %2d hours — %s%n",
                    p.getDisplayName(), p.getSlaHours(), p.getDescription());
        }
    }

    // ─── Incident Menu ────────────────────────────────────────────────────────

    private void showIncidentMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n" + "─".repeat(60));
            System.out.println("  INCIDENT MANAGEMENT");
            System.out.println("─".repeat(60));
            System.out.println("  [1] Auto-generate tickets from log analysis");
            System.out.println("  [2] Generate unified incident summary report");
            System.out.println("  [3] Show auto-generated tickets");
            System.out.println("  [0] Back");
            System.out.println("─".repeat(60));

            switch (prompt("Enter choice").trim()) {
                case "1" -> autoGenerateTickets();
                case "2" -> System.out.println(incidentService.generateIncidentSummaryReport());
                case "3" -> showAutoTickets();
                case "0" -> back = true;
                default -> printError("Invalid choice.");
            }
        }
    }

    private void autoGenerateTickets() {
        if (!logAnalyzer.hasData()) {
            printError("No log data loaded. Please analyze a log file first (Main Menu → 1 → 1).");
            return;
        }
        System.out.println("\n  Auto-generating tickets from detected log errors...");
        List<Ticket> tickets = incidentService.generateTicketsFromLogs();
        if (tickets.isEmpty()) {
            System.out.println("  No new tickets generated (no classified errors found).");
        } else {
            printSuccess(tickets.size() + " ticket(s) generated:");
            tickets.forEach(t -> System.out.printf("    • %s — %s [%s]%n",
                    t.getTicketId(), t.getTitle(), t.getPriority()));
        }
    }

    private void showAutoTickets() {
        List<Ticket> tickets = ticketService.getAutoGeneratedTickets();
        if (tickets.isEmpty()) {
            System.out.println("\n  No auto-generated tickets found.");
            return;
        }
        System.out.println("\n  AUTO-GENERATED TICKETS:");
        tickets.forEach(t -> System.out.println("  " + t.getSummaryLine()));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String prompt(String label) {
        System.out.print("\n  " + label + ": ");
        return scanner.hasNextLine() ? scanner.nextLine() : "";
    }

    private int readInt(String label, int min, int max) {
        while (true) {
            try {
                int val = Integer.parseInt(prompt(label).trim());
                if (val >= min && val <= max)
                    return val;
                printError("Please enter a number between " + min + " and " + max);
            } catch (NumberFormatException e) {
                printError("Invalid number. Try again.");
            }
        }
    }

    private void printSuccess(String msg) {
        System.out.println("\n  ✓ " + msg);
    }

    private void printError(String msg) {
        System.out.println("\n  ✗ ERROR: " + msg);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void printBanner() {
        System.out.println("""

                ╔══════════════════════════════════════════════════════════════╗
                ║   Java REST API Log Analyzer & SLA Ticket Simulator          ║
                ║   Production-Style Incident Management System                 ║
                ║   Version 1.0.0  |  Java SE 17                               ║
                ╚══════════════════════════════════════════════════════════════╝
                """);
    }
}
