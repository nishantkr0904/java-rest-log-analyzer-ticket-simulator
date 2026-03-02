package com.loganalyzer;

import com.loganalyzer.models.*;
import com.loganalyzer.services.*;
import com.loganalyzer.sla.*;

import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test covering the full pipeline:
 * log parsing → error classification → ticket generation → SLA evaluation
 */
class FullPipelineIntegrationTest {

    private LogAnalyzerService analyzer;
    private TicketService ticketService;
    private SlaComplianceEngine slaEngine;
    private IncidentManagementService incidentService;

    @BeforeEach
    void setUp() {
        analyzer = new LogAnalyzerService();
        ticketService = new TicketService();
        slaEngine = new SlaComplianceEngine();
        incidentService = new IncidentManagementService(analyzer, ticketService, slaEngine);
    }

    @Test
    void testJsonLogParsing() throws Exception {
        analyzer.analyze(Paths.get("sample_logs/api_logs.json"));
        assertTrue(analyzer.getTotalEntries() > 0, "Should parse log entries");
        assertTrue(analyzer.getTotalErrors() > 0, "Should detect errors");
    }

    @Test
    void testRootCauseClassification() throws Exception {
        analyzer.analyze(Paths.get("sample_logs/api_logs.json"));
        boolean allClassified = analyzer.getErrorEntries().stream()
                .allMatch(e -> e.getDetectedErrorType() != null);
        assertTrue(allClassified, "Every error entry should be classified");
    }

    @Test
    void testErrorFrequencyAggregation() throws Exception {
        analyzer.analyze(Paths.get("sample_logs/api_logs.json"));
        assertFalse(analyzer.getErrorFrequency().isEmpty(), "Error frequency map should not be empty");
    }

    @Test
    void testAutoTicketGeneration() throws Exception {
        analyzer.analyze(Paths.get("sample_logs/api_logs.json"));
        List<Ticket> tickets = incidentService.generateTicketsFromLogs();
        assertFalse(tickets.isEmpty(), "Should generate at least one ticket");
        tickets.forEach(t -> assertNotNull(t.getTicketId(), "Ticket ID should not be null"));
    }

    @Test
    void testSlaEvaluation() throws Exception {
        analyzer.analyze(Paths.get("sample_logs/api_logs.json"));
        incidentService.generateTicketsFromLogs();
        SlaComplianceEngine.SlaReport report = slaEngine.evaluate(ticketService.getAllTickets());
        assertTrue(report.totalTickets > 0, "SLA report should cover all generated tickets");
        assertTrue(report.complianceRate >= 0 && report.complianceRate <= 100,
                "Compliance rate should be between 0 and 100");
    }

    @Test
    void testPlainTextLogParsing() throws Exception {
        analyzer.analyze(Paths.get("sample_logs/application.log"));
        assertTrue(analyzer.getTotalEntries() > 0, "Should parse plain-text log entries");
        assertTrue(analyzer.getTotalErrors() > 0, "Should detect errors in plain-text logs");
    }

    @Test
    void testDatabaseErrorDetection() throws Exception {
        analyzer.analyze(Paths.get("sample_logs/db_errors.json"));
        long dbErrors = analyzer.getErrorEntries().stream()
                .filter(e -> e.getDetectedErrorType() != null && e.getDetectedErrorType().isDatabaseError())
                .count();
        assertTrue(dbErrors >= 3, "Should detect at least 3 database errors, got: " + dbErrors);
    }

    @Test
    void testIncidentSummaryReport() throws Exception {
        analyzer.analyze(Paths.get("sample_logs/api_logs.json"));
        incidentService.generateTicketsFromLogs();
        String report = incidentService.generateIncidentSummaryReport();
        assertTrue(report.contains("UNIFIED INCIDENT SUMMARY"), "Report should contain header");
        assertTrue(report.contains("TICKET STATUS SUMMARY"), "Report should contain ticket section");
        assertTrue(report.contains("SLA COMPLIANCE"), "Report should contain SLA section");
    }

    @Test
    void testTicketStatusTransitions() {
        Ticket t = ticketService.createTicket("Test", "desc",
                ErrorType.HTTP_500_INTERNAL_SERVER_ERROR, Priority.HIGH);
        assertEquals(TicketStatus.OPEN, t.getStatus());

        ticketService.updateStatus(t.getTicketId(), TicketStatus.IN_PROGRESS);
        assertEquals(TicketStatus.IN_PROGRESS, t.getStatus());

        ticketService.resolveTicket(t.getTicketId(), "Fixed");
        assertEquals(TicketStatus.RESOLVED, t.getStatus());
        assertNotNull(t.getResolvedAt());
    }

    @Test
    void testSlaBreachDetection() {
        // Critical SLA = 2 hours; fresh ticket should not be breached
        Ticket t = ticketService.createTicket("Critical issue", "desc",
                ErrorType.DB_CONNECTION_FAILURE, Priority.CRITICAL);
        assertFalse(t.isSlaBreached(), "Brand-new ticket should not be breached");
        assertTrue(t.getSlaRemainingMinutes() > 0, "Should have time remaining");
    }
}
