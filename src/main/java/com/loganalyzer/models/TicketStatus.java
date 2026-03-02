package com.loganalyzer.models;

/**
 * Represents the lifecycle status of a support/incident ticket.
 */
public enum TicketStatus {

    OPEN("Open", "Ticket has been created and is awaiting assignment"),
    IN_PROGRESS("In Progress", "Ticket is actively being worked on"),
    PENDING_REVIEW("Pending Review", "Fix has been applied, awaiting verification"),
    RESOLVED("Resolved", "Issue has been resolved and confirmed"),
    CLOSED("Closed", "Ticket has been closed after resolution confirmation"),
    REOPENED("Reopened", "Ticket was re-opened due to recurrence or unresolved issue");

    private final String displayName;
    private final String description;

    TicketStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    /**
     * Returns true if the ticket is in a terminal (non-active) state.
     */
    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }

    /**
     * Returns true if this is a valid transition from the given status.
     */
    public boolean isValidTransitionFrom(TicketStatus current) {
        return switch (current) {
            case OPEN -> this == IN_PROGRESS || this == CLOSED;
            case IN_PROGRESS -> this == PENDING_REVIEW || this == RESOLVED || this == OPEN;
            case PENDING_REVIEW -> this == RESOLVED || this == IN_PROGRESS || this == REOPENED;
            case RESOLVED -> this == CLOSED || this == REOPENED;
            case CLOSED -> this == REOPENED;
            case REOPENED -> this == IN_PROGRESS || this == CLOSED;
        };
    }

    @Override
    public String toString() { return displayName; }
}
