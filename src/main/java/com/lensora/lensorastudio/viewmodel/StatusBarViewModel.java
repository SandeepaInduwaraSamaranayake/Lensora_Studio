package com.lensora.lensorastudio.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class StatusBarViewModel
{
    private final StringProperty statusText      = new SimpleStringProperty("Ready");
    private final StringProperty projectsCount   = new SimpleStringProperty("Projects: 0");
    private final StringProperty remindersCount  = new SimpleStringProperty("Reminders: 0");
    private final StringProperty currentPath     = new SimpleStringProperty("");

    public StringProperty statusTextProperty()     { return statusText; }
    public StringProperty projectsCountProperty()  { return projectsCount; }
    public StringProperty remindersCountProperty() { return remindersCount; }
    public StringProperty currentPathProperty()    { return currentPath; }
}