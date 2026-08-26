package com.lensora.lensorastudio.core.context;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import java.io.File;

public class ProjectContext 
{
    private final ObjectProperty<File> projectRoot = new SimpleObjectProperty<>();

    public ReadOnlyObjectProperty<File> projectRootProperty() { return projectRoot; }
    public File getProjectRoot() { return projectRoot.get(); }
    public void setProjectRoot(File root) { this.projectRoot.set(root); }
}