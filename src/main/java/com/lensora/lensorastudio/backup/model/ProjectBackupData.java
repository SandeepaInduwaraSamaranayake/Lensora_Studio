package com.lensora.lensorastudio.backup.model;

import java.util.List;
import java.util.Map;

/** Project-scoped database rows, serialized as JSON inside the .lsbak archive. */
public class ProjectBackupData
{
    public Map<String, Object>       project;
    public List<Map<String, Object>> notes;
    public List<Map<String, Object>> reminders;
    public List<Map<String, Object>> payments;
    public List<Map<String, Object>> deliverables;
}