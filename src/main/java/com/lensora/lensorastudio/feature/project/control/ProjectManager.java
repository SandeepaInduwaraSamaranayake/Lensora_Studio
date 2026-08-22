package com.lensora.lensorastudio.feature.project.control;

import com.lensora.lensorastudio.core.config.AppSettings;
import com.lensora.lensorastudio.feature.project.model.Project;
import com.lensora.lensorastudio.feature.project.repository.ProjectRepository;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;

import javafx.collections.FXCollections;
import java.util.function.Predicate;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class ProjectManager
{
    private static final Logger logger = LoggerFactory.getLogger(ProjectManager.class);

    private final TableView<Project>                projectTable;

    private final TableColumn<Project, String>      colProjectNumber, 
                                                    colClientName, 
                                                    colStatus;

    private final TextField                         detProjectNumber, 
                                                    detClientName, 
                                                    detClientPhone,
                                                    detClientEmail, 
                                                    detEventType, 
                                                    detEventDate, 
                                                    detDueDate,
                                                    detStatus, 
                                                    detPackage, 
                                                    detProjectPath, 
                                                    detTotalAmount,
                                                    detAdvanceAmount, 
                                                    detBalanceAmount;

    private final TextArea                          detRemarks;

    private final HBox                              progressStepper;

    private final Label                             lblStatusPath, 
                                                    lblFolderHeader, 
                                                    lblStatusProjects, 
                                                    lblProjectCount, 
                                                    lblStatusText;

    private final SplitPane                         projectWorkspace;

    private final VBox                              emptyStatePane;


    private final AppSettings                       settings;
    private Project                                 currentProject;
    private Runnable                                onProjectSelected;
    private String                                  currentSearchQuery = null;
    private final FilteredList<Project>             filteredProjects;

    private final ObservableList<Project> allProjects = FXCollections.observableArrayList();
    private String currentStatusFilter= "All Statuses";


        public ProjectManager(  TableView<Project> projectTable,
                                TableColumn<Project, String> colProjectNumber,
                                TableColumn<Project, String> colClientName,
                                TableColumn<Project, String> colStatus,
                                TextField detProjectNumber, TextField detClientName,
                                TextField detClientPhone, TextField detClientEmail,
                                TextField detEventType, TextField detEventDate,
                                TextField detDueDate, TextField detStatus,
                                TextField detPackage, TextField detProjectPath,
                                TextField detTotalAmount, TextField detAdvanceAmount,
                                TextField detBalanceAmount, TextArea detRemarks,
                                HBox progressStepper,
                                Label lblStatusPath, Label lblFolderHeader,
                                Label lblStatusProjects, Label lblProjectCount,
                                Label lblStatusText,
                                SplitPane projectWorkspace, VBox emptyStatePane) 
        {
            this.projectTable = projectTable;
            this.colProjectNumber = colProjectNumber;
            this.colClientName = colClientName;
            this.colStatus = colStatus;
            this.detProjectNumber = detProjectNumber;
            this.detClientName = detClientName;
            this.detClientPhone = detClientPhone;
            this.detClientEmail = detClientEmail;
            this.detEventType = detEventType;
            this.detEventDate = detEventDate;
            this.detDueDate = detDueDate;
            this.detStatus = detStatus;
            this.detPackage = detPackage;
            this.detProjectPath = detProjectPath;
            this.detTotalAmount = detTotalAmount;
            this.detAdvanceAmount = detAdvanceAmount;
            this.detBalanceAmount = detBalanceAmount;
            this.detRemarks = detRemarks;
            this.progressStepper = progressStepper;
            this.lblStatusPath = lblStatusPath;
            this.lblFolderHeader = lblFolderHeader;
            this.lblStatusProjects = lblStatusProjects;
            this.lblProjectCount = lblProjectCount;
            this.lblStatusText = lblStatusText;
            this.projectWorkspace = projectWorkspace;
            this.emptyStatePane = emptyStatePane;
            this.settings = AppSettings.getInstance();

            setupSelectionListener();
            setupCellFactories();

            filteredProjects = new FilteredList<>(allProjects);
            projectTable.setItems(filteredProjects);
    }

    /**
     * #######################################################################
     * #######################################################################
     * ################## Tables Cell Value Factories ########################
     * #######################################################################
     * #######################################################################
     */
    private void setupCellFactories()
    {
        colProjectNumber.setCellValueFactory(cellData -> cellData.getValue().projectNumberProperty());
        colClientName.setCellValueFactory(cellData -> cellData.getValue().clientNameProperty());
        colStatus.setCellValueFactory(cellData -> cellData.getValue().projectStatusProperty());
    }

    // Project Table Refresher Method
    public void refreshProjectList() 
    {
        try
        {
            List<Project> projects = ProjectRepository.findAll();
            allProjects.setAll(projects);  // replace the master list
            updateCounts(projects.size());
            restoreLastProject(projects);
            applyFilter(); // reapply current filters
        } 
        catch (SQLException e) 
        {
            logger.error("Failed to refresh project list", e);
            ErrorHandler.show(null, "Failed to refresh projects", e);
        }
    }

    private void updateCounts(int count) 
    {
        lblStatusProjects.setText("Projects: " + count);
        lblProjectCount.setText(count + " projects");
        if (count == 0) 
        {
            lblStatusText.setText("No projects");
        } 
        else 
        {
            lblStatusText.setText("Ready");
        }
    }

    private void loadProjectDetails(Project project) 
    {
        detProjectNumber.setText(project.getProjectNumber());
        detClientName.setText(project.getClientName());
        detClientPhone.setText(project.getClientPhone());
        detClientEmail.setText(project.getClientEmail());
        detEventType.setText(project.getEventType());
        detEventDate.setText(project.getEventDate() != null ? project.getEventDate().toString() : "");
        detDueDate.setText(project.getDueDate() != null ? project.getDueDate().toString() : "");
        detStatus.setText(project.getProjectStatus());
        detPackage.setText(project.getPackageName());
        detProjectPath.setText(project.getProjectPath());
        detTotalAmount.setText(project.getTotalAmount() != null ? project.getTotalAmount().toString() : "0.00");
        detAdvanceAmount.setText(project.getAdvanceAmount() != null ? project.getAdvanceAmount().toString() : "0.00");
        detBalanceAmount.setText(project.getBalanceAmount() != null ? project.getBalanceAmount().toString() : "0.00");
        detRemarks.setText(project.getRemarks());

        // Update progress stepper based on status
        updateProgressStepper(project.getProjectStatus());

        // Update status labels
        lblStatusPath.setText(project.getProjectPath());

        // Update folder header with the full path
        lblFolderHeader.setText("Folders  [" + project.getProjectPath() + "]");
    }

    // Check and restore last opened project
    private void restoreLastProject(List<Project> projects) 
    {
        if (!settings.getOpenLastProject() || projects.isEmpty()) return;
        int lastId = settings.getLastProjectId();
        Project target = projects.stream()
            .filter(p -> p.getProjectId() == lastId)
            .findFirst()
            .orElse(projects.get(0));
        projectTable.getSelectionModel().select(target);
    }

    private void updateProgressStepper(String status)
    {
        // just show status as a label, later we can create a visual stepper.
        progressStepper.getChildren().clear();
        Label statusLabel = new Label("Status: " + status);
        statusLabel.setStyle("-fx-font-weight: bold;");
        progressStepper.getChildren().add(statusLabel);
    }

    private void showWorkspace(boolean show) 
    {
        projectWorkspace.setVisible(show);
        projectWorkspace.setManaged(show);
        emptyStatePane.setVisible(!show);
        emptyStatePane.setManaged(!show);
    }

    /**
     * Sets up the project table selection listener.
     */
    private void setupSelectionListener() 
    {
        projectTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) 
            {
                // Save the selected project ID
                settings.setLastProjectId(newVal.getProjectId());
                loadProjectDetails(newVal);
                currentProject = newVal;
                showWorkspace(true);
                if (onProjectSelected != null) 
                {
                    onProjectSelected.run();
                }
            } 
            else 
            {
                showWorkspace(false);
                currentProject = null;
                // clears all detail fields and status labels
                clearProjectDetails();
            }
        });
    }

    public void refreshCurrentProjectDetails() 
    {
        if (currentProject != null) 
        {
            // Reload from DB to get updated data
            try 
            {
                Project reloaded = ProjectRepository.findById(currentProject.getProjectId());
                if (reloaded != null) 
                {
                    loadProjectDetails(reloaded);
                    currentProject = reloaded;
                }
            } 
            catch (SQLException e) 
            {
                logger.error("Failed to reload project details", e);
            }
        }
    }

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


    private void applyFilter() 
    {
        Predicate<Project> predicate = project -> {
            // Status filter
            if (currentStatusFilter != null && !currentStatusFilter.equals("All Statuses") 
                && !currentStatusFilter.equals(project.getProjectStatus())) 
            {
                return false;
            }
            // Text search filter
            return matchesSearch(project, currentSearchQuery);
        };
        filteredProjects.setPredicate(predicate);
        // Update counts based on filtered size
        int filteredSize = filteredProjects.size();
        lblStatusProjects.setText("Projects: " + filteredSize);
        lblProjectCount.setText(filteredSize + " projects");
        lblStatusText.setText(filteredSize == 0 ? "No projects" : "Ready");
    }

    private boolean matchesSearch(Project project, String query)
    {
        if (query != null && !query.isEmpty()) 
        {
            String q = query.toLowerCase();
            return project.getProjectNumber().toLowerCase().contains(q)
                    || project.getClientName().toLowerCase().contains(q)
                    || (project.getClientPhone() != null && project.getClientPhone().toLowerCase().contains(q))
                    || (project.getProjectStatus() != null && project.getProjectStatus().toLowerCase().contains(q))
                    || (project.getEventType() != null && project.getEventType().toLowerCase().contains(q))
                    || (project.getClientEmail() != null && project.getClientEmail().toLowerCase().contains(q))
                    || (project.getEventDate() != null && project.getEventDate().toString().contains(q));
        }
        return true;
    }

    public void resetSearchWithoutClearingSelection() 
    {
        this.currentSearchQuery = null;
        applyFilter();
    }

    private void clearProjectDetails() 
    {
        detProjectNumber.setText("");
        detClientName.setText("");
        detClientPhone.setText("");
        detClientEmail.setText("");
        detEventType.setText("");
        detEventDate.setText("");
        detDueDate.setText("");
        detStatus.setText("");
        detPackage.setText("");
        detProjectPath.setText("");
        detTotalAmount.setText("");
        detAdvanceAmount.setText("");
        detBalanceAmount.setText("");
        detRemarks.setText("");
        progressStepper.getChildren().clear();
        lblStatusPath.setText("");
        lblFolderHeader.setText("Folders");
    }

    public void setOnProjectSelected(Runnable onProjectSelected) 
    {
        this.onProjectSelected = onProjectSelected;
    }

    public Project getCurrentProject() 
    {
        return currentProject;
    }
}