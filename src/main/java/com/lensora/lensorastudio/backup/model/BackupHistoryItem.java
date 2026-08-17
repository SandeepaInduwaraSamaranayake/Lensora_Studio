package com.lensora.lensorastudio.backup.model;

import java.time.LocalDateTime;

public class BackupHistoryItem
{
    private int historyId;
    private Integer scheduleId;
    private String scheduleName; // null = manual backup
    private int projectId;
    private String projectNumber;
    private String clientName;
    private String filePath;
    private long fileSize;
    private int totalFiles;
    private String status; // "SUCCEEDED" / "FAILED"
    private String errorMessage;
    private boolean verified;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    // getters/setters
    public int getHistoryId()                       { return historyId; }
    public void setHistoryId(int v)                 { historyId = v; }
    public Integer getScheduleId()                  { return scheduleId; }
    public void setScheduleId(Integer v)            { scheduleId = v; }
    public String getScheduleName()                 { return scheduleName; }
    public void setScheduleName(String v)           { scheduleName = v; }
    public int getProjectId()                       { return projectId; }
    public void setProjectId(int v)                 { projectId = v; }
    public String getProjectNumber()                { return projectNumber; }
    public void setProjectNumber(String v)          { projectNumber = v; }
    public String getClientName()                   { return clientName; }
    public void setClientName(String v)             { clientName = v; }
    public String getFilePath()                     { return filePath; }
    public void setFilePath(String v)               { filePath = v; }
    public long getFileSize()                       { return fileSize; }
    public void setFileSize(long v)                 { fileSize = v; }
    public int getTotalFiles()                      { return totalFiles; }
    public void setTotalFiles(int v)                { totalFiles = v; }
    public String getStatus()                       { return status; }
    public void setStatus(String v)                 { status = v; }
    public String getErrorMessage()                 { return errorMessage; }
    public void setErrorMessage(String v)           { errorMessage = v; }
    public boolean isVerified()                     { return verified; }
    public void setVerified(boolean v)              { verified = v; }
    public LocalDateTime getStartedAt()             { return startedAt; }
    public void setStartedAt(LocalDateTime v)       { startedAt = v; }
    public LocalDateTime getCompletedAt()           { return completedAt; }
    public void setCompletedAt(LocalDateTime v)     { completedAt = v; }

    public String describeSource()
    {
        return scheduleName != null ? "Scheduled: " + scheduleName : "Manual";
    }
}