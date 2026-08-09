package com.lensora.lensorastudio.model;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Project
{

 // ─── Primary key ────────────────────────────────────────────────────────
    private int           projectId;

    // ─── Identity ─────────────────────────────────────────────────────────────
    private String        projectNumber;
    private String        clientName;
    private String        clientPhone;
    private String        clientEmail;

    // ─── Event ────────────────────────────────────────────────────────────────
    private String        eventType;
    private LocalDate     eventDate;
    private LocalDate     dueDate;

    // ─── State ────────────────────────────────────────────────────────────────
    private String        projectStatus;
    private String        projectPath;

    // ─── Financials ───────────────────────────────────────────────────────────
    private String        packageName;
    private BigDecimal    totalAmount;
    private BigDecimal    advanceAmount;
    private BigDecimal    balanceAmount;

    // ─── Misc ─────────────────────────────────────────────────────────────────
    private String        remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ─── Constructors ─────────────────────────────────────────────────────────

    public Project() {}

    /** Minimal constructor used when creating a new project. */
    public Project(String projectNumber, String clientName, String eventType,
        LocalDate eventDate, String projectStatus, String projectPath)
    {
        this.projectNumber = projectNumber;
        this.clientName    = clientName;
        this.eventType     = eventType;
        this.eventDate     = eventDate;
        this.projectStatus = projectStatus;
        this.projectPath   = projectPath;
        this.createdAt     = LocalDateTime.now();
        this.updatedAt     = LocalDateTime.now();
    }

    // ─── Status constants ─────────────────────────────────────────────────────

    public static final String STATUS_BOOKED       = "Booked";
    public static final String STATUS_SHOT         = "Shot";
    public static final String STATUS_SELECTING    = "Selecting";
    public static final String STATUS_EDITING      = "Editing";
    public static final String STATUS_ALBUM_DESIGN = "Album Design";
    public static final String STATUS_PRINTING     = "Printing";
    public static final String STATUS_READY        = "Ready";
    public static final String STATUS_DELIVERED    = "Delivered";
    public static final String STATUS_CLOSED       = "Closed";

    public static final String[] ALL_STATUSES = {
        STATUS_BOOKED, STATUS_SHOT, STATUS_SELECTING, STATUS_EDITING,
        STATUS_ALBUM_DESIGN, STATUS_PRINTING, STATUS_READY,
        STATUS_DELIVERED, STATUS_CLOSED
    };

    // ─── Event type constants ─────────────────────────────────────────────────

    public static final String TYPE_WEDDING    = "Wedding";
    public static final String TYPE_EVENT      = "Event";
    public static final String TYPE_GRADUATION = "Graduation";
    public static final String TYPE_CUSTOM     = "Custom";

    public static final String[] ALL_TYPES = {
        TYPE_WEDDING, TYPE_EVENT, TYPE_GRADUATION, TYPE_CUSTOM
    };

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public int           getProjectId()                 { return projectId; }
    public void          setProjectId(int v)            { this.projectId = v; }

    public String        getProjectNumber()             { return projectNumber; }
    public void          setProjectNumber(String v)     { this.projectNumber = v; }

    public String        getClientName()                { return clientName; }
    public void          setClientName(String v)        { this.clientName = v; }

    public String        getClientPhone()               { return clientPhone; }
    public void          setClientPhone(String v)       { this.clientPhone = v; }

    public String        getClientEmail()               { return clientEmail; }
    public void          setClientEmail(String v)       { this.clientEmail = v; }

    public String        getEventType()                 { return eventType; }
    public void          setEventType(String v)         { this.eventType = v; }

    public LocalDate     getEventDate()                 { return eventDate; }
    public void          setEventDate(LocalDate v)      { this.eventDate = v; }

    public LocalDate     getDueDate()                   { return dueDate; }
    public void          setDueDate(LocalDate v)        { this.dueDate = v; }

    public String        getProjectStatus()             { return projectStatus; }
    public void          setProjectStatus(String v)     { this.projectStatus = v; }

    public String        getProjectPath()               { return projectPath; }
    public void          setProjectPath(String v)       { this.projectPath = v; }

    public String        getPackageName()               { return packageName; }
    public void          setPackageName(String v)       { this.packageName = v; }

    public BigDecimal    getTotalAmount()               { return totalAmount; }
    public void          setTotalAmount(BigDecimal v)   { this.totalAmount = v; }

    public BigDecimal    getAdvanceAmount()             { return advanceAmount; }
    public void          setAdvanceAmount(BigDecimal v) { this.advanceAmount = v; }

    public BigDecimal    getBalanceAmount()             { return balanceAmount; }
    public void          setBalanceAmount(BigDecimal v) { this.balanceAmount = v; }

    public String        getRemarks()                   { return remarks; }
    public void          setRemarks(String v)           { this.remarks = v; }

    public LocalDateTime getCreatedAt()                 { return createdAt; }
    public void          setCreatedAt(LocalDateTime v)  { this.createdAt = v; }

    public LocalDateTime getUpdatedAt()                 { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime v)  { this.updatedAt = v; }

    // ─── Convenience ──────────────────────────────────────────────────────────

    /** Returns a short display string for the project table. */
    @Override
    public String toString()
    {
        return projectNumber + " - " + clientName;
    }

    // ─── Property Getters ──────────────────────────────────────────────────────
    public StringProperty projectNumberProperty() 
    {
        return new SimpleStringProperty(projectNumber);
    }

    public StringProperty clientNameProperty() 
    {
        return new SimpleStringProperty(clientName);
    }
    public StringProperty projectStatusProperty() 
    {
        return new SimpleStringProperty(projectStatus);
    }
}
