package com.loganalyzer.models;

/**
 * Ticket priority levels with associated SLA resolution time windows.
 * SLA hours define the maximum time (in hours) allowed to resolve a ticket.
 */
public enum Priority {

    LOW("Low", 72, "Non-urgent issue; resolve within 3 business days"),
    MEDIUM("Medium", 24, "Moderate impact; resolve within 1 business day"),
    HIGH("High", 8, "Significant impact; resolve within 8 hours"),
    CRITICAL("Critical", 2, "Service-affecting incident; resolve within 2 hours");

    private final String displayName;
    private final int slaHours;
    private final String description;

    Priority(String displayName, int slaHours, String description) {
        this.displayName = displayName;
        this.slaHours = slaHours;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public int getSlaHours() { return slaHours; }
    public String getDescription() { return description; }

    @Override
    public String toString() { return displayName; }
}
