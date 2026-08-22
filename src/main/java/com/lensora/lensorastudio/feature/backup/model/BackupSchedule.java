package com.lensora.lensorastudio.feature.backup.model;

import java.time.LocalDateTime;
import java.util.List;

public class BackupSchedule
{
    public enum Scope           { SINGLE, MULTIPLE, ALL }
    public enum Frequency       { HOURLY, DAILY, WEEKLY }
    public enum RunStatus       { IDLE, SCHEDULED, RUNNING, SUCCEEDED, FAILED }

    private int scheduleId;
    private String name;
    private Scope scope;
    private List<Integer> projectIds; // null when scope == ALL
    private String destinationPath;
    private Frequency frequency;
    private int intervalValue = 1;    // e.g. every 2 hours, every 3 days
    private String timeOfDay;         // "HH:mm", for DAILY/WEEKLY
    private Integer dayOfWeek;        // 1=Mon..7=Sun, for WEEKLY
    private boolean enabled = true;
    private LocalDateTime lastRun;
    private LocalDateTime nextRun;
    private LocalDateTime createdAt;
    // Transient-not persisted, purely for live UI display.
    private transient RunStatus runStatus = RunStatus.IDLE;
    private transient String runStatusMessage = "";

    // getters/setters
    public int getScheduleId()                      { return scheduleId; }
    public void setScheduleId(int v)                { scheduleId = v; }
    public String getName()                         { return name; }
    public void setName(String v)                   { name = v; }
    public Scope getScope()                         { return scope; }
    public void setScope(Scope v)                   { scope = v; }
    public List<Integer> getProjectIds()            { return projectIds; }
    public void setProjectIds(List<Integer> v)      { projectIds = v; }
    public String getDestinationPath()              { return destinationPath; }
    public void setDestinationPath(String v)        { destinationPath = v; }
    public Frequency getFrequency()                 { return frequency; }
    public void setFrequency(Frequency v)           { frequency = v; }
    public int getIntervalValue()                   { return intervalValue; }
    public void setIntervalValue(int v)             { intervalValue = v; }
    public String getTimeOfDay()                    { return timeOfDay; }
    public void setTimeOfDay(String v)              { timeOfDay = v; }
    public Integer getDayOfWeek()                   { return dayOfWeek; }
    public void setDayOfWeek(Integer v)             { dayOfWeek = v; }
    public boolean isEnabled()                      { return enabled; }
    public void setEnabled(boolean v)               { enabled = v; }
    public LocalDateTime getLastRun()               { return lastRun; }
    public void setLastRun(LocalDateTime v)         { lastRun = v; }
    public LocalDateTime getNextRun()               { return nextRun; }
    public void setNextRun(LocalDateTime v)         { nextRun = v; }
    public LocalDateTime getCreatedAt()             { return createdAt; }
    public void setCreatedAt(LocalDateTime v)       { createdAt = v; }
    public RunStatus getRunStatus()                 { return runStatus; }
    public void setRunStatus(RunStatus v)           { runStatus = v; }
    public String getRunStatusMessage()             { return runStatusMessage; }
    public void setRunStatusMessage(String v)       { runStatusMessage = v; }

    public String describeFrequency()
    {
        return switch (frequency)
        {
            case HOURLY -> "Every " + intervalValue + " hour(s)";
            case DAILY -> "Every " + intervalValue + " day(s) at " + timeOfDay;
            case WEEKLY -> "Weekly on " + dayName(dayOfWeek) + " at " + timeOfDay;
        };
    }

    private String dayName(Integer d)
    {
        String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        return (d != null && d >= 1 && d <= 7) ? names[d - 1] : "?";
    }
}