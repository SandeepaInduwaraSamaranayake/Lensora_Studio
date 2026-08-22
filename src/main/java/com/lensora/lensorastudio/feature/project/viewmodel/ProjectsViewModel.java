package com.lensora.lensorastudio.feature.project.viewmodel;

import com.lensora.lensorastudio.core.config.AppSettings;
import com.lensora.lensorastudio.feature.project.model.Project;
import com.lensora.lensorastudio.feature.project.repository.ProjectRepository;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Predicate;

/**
 * UI-agnostic holder for project list state: the master list, the current
 * filter/search predicate, and the selected project.
 *
 * No JavaFX Control classes are referenced here (no TableView, no Label) -
 * that's what makes this safe to bind from more than one dockable panel
 * (ProjectListController and ProjectDetailsController) at once.
 */
public class ProjectsViewModel
{
    private static final Logger logger = LoggerFactory.getLogger(ProjectsViewModel.class);

    private final ObservableList<Project> allProjects = FXCollections.observableArrayList();
    private final FilteredList<Project> filteredProjects = new FilteredList<>(allProjects);

    private final ObjectProperty<Project> selectedProject = new SimpleObjectProperty<>();
    private final StringProperty statusText = new SimpleStringProperty("Ready");

    private String currentSearchQuery = null;
    private String currentStatusFilter = "All Statuses";

    private final AppSettings settings = AppSettings.getInstance();

    // ─── Data access ────────────────────────────────────────────────────────

    public ObservableList<Project> getFilteredProjects()
    {
        return filteredProjects;
    }

    public ObjectProperty<Project> selectedProjectProperty()
    {
        return selectedProject;
    }

    public Project getSelectedProject()
    {
        return selectedProject.get();
    }

    public void setSelectedProject(Project project)
    {
        // Early exit if the project reference hasn't changed
        if (selectedProject.get() == project)
        {
            return;
        }
        
        selectedProject.set(project);
        if (project != null)
        {
            settings.setLastProjectId(project.getProjectId());
        }
    }

    public StringProperty statusTextProperty()
    {
        return statusText;
    }

    // ─── Loading ────────────────────────────────────────────────────────────

    public void refresh()
    {
        try
        {
            List<Project> projects = ProjectRepository.findAll();
            allProjects.setAll(projects);
            applyFilter();
            restoreLastProject(projects);
            statusText.set(projects.isEmpty() ? "No projects" : "Ready");
        }
        catch (SQLException e)
        {
            logger.error("Failed to refresh project list", e);
            ErrorHandler.show(null, "Failed to refresh projects", e);
        }
    }

    public void refreshSelectedFromDb()
    {
        Project current = selectedProject.get();
        if (current == null) return;
        try
        {
            Project reloaded = ProjectRepository.findById(current.getProjectId());
            if (reloaded != null)
            {
                selectedProject.set(reloaded);
            }
        }
        catch (SQLException e)
        {
            logger.error("Failed to reload project details", e);
        }
    }

    private void restoreLastProject(List<Project> projects)
    {
        if (!settings.getOpenLastProject() || projects.isEmpty()) return;
        int lastId = settings.getLastProjectId();
        Project target = projects.stream()
                .filter(p -> p.getProjectId() == lastId)
                .findFirst()
                .orElse(projects.get(0));
        setSelectedProject(target);
    }

    public void selectById(int projectId)
    {
        for (Project p : allProjects)
        {
            if (p.getProjectId() == projectId)
            {
                setSelectedProject(p);
                return;
            }
        }
    }

    // ─── Filtering ──────────────────────────────────────────────────────────

    public void searchProjects(String query)
    {
        this.currentSearchQuery = (query == null || query.trim().isEmpty()) ? null : query.trim();
        applyFilter();
    }

    public void filterByStatus(String status)
    {
        this.currentStatusFilter = status;
        applyFilter();
    }

    public void resetSearchWithoutClearingSelection()
    {
        this.currentSearchQuery = null;
        applyFilter();
    }

    private void applyFilter()
    {
        Predicate<Project> predicate = project -> {
            if (currentStatusFilter != null && !currentStatusFilter.equals("All Statuses")
                    && !currentStatusFilter.equals(project.getProjectStatus()))
            {
                return false;
            }
            return matchesSearch(project, currentSearchQuery);
        };
        filteredProjects.setPredicate(predicate);
        int size = filteredProjects.size();
        statusText.set(size == 0 ? "No projects" : "Ready");
    }

    private boolean matchesSearch(Project project, String query)
    {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase();
        return project.getProjectNumber().toLowerCase().contains(q)
                || project.getClientName().toLowerCase().contains(q)
                || (project.getClientPhone() != null && project.getClientPhone().toLowerCase().contains(q))
                || (project.getProjectStatus() != null && project.getProjectStatus().toLowerCase().contains(q))
                || (project.getEventType() != null && project.getEventType().toLowerCase().contains(q))
                || (project.getClientEmail() != null && project.getClientEmail().toLowerCase().contains(q))
                || (project.getEventDate() != null && project.getEventDate().toString().contains(q));
    }

    public int getTotalCount()
    {
        return allProjects.size();
    }

    public int getFilteredCount()
    {
        return filteredProjects.size();
    }
}