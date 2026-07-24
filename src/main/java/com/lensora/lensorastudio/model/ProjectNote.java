package com.lensora.lensorastudio.model;

import java.time.LocalDateTime;

public class ProjectNote
{
    private int noteId;
    private int projectId;
    private String noteTitle;
    private String noteContent;
    private LocalDateTime createdAt;

    public ProjectNote() {}

    public ProjectNote(int projectId, String noteTitle, String noteContent)
    {
        this.projectId = projectId;
        this.noteTitle = noteTitle;
        this.noteContent = noteContent;
        this.createdAt = LocalDateTime.now();
    }

    public int getNoteId()                          { return noteId; }
    public void setNoteId(int v)                    { this.noteId = v; }

    public int getProjectId()                       { return projectId; }
    public void setProjectId(int v)                 { this.projectId = v; }

    public String getNoteTitle()                    { return noteTitle; }
    public void setNoteTitle(String v)              { this.noteTitle = v; }

    public String getNoteContent()                  { return noteContent; }
    public void setNoteContent(String v)            { this.noteContent = v; }

    public LocalDateTime getCreatedAt()             { return createdAt; }
    public void setCreatedAt(LocalDateTime v)       { this.createdAt = v; }
}