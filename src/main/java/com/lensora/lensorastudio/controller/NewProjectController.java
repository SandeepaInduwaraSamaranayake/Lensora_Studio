package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.repository.FolderTemplateRepository;
import com.lensora.lensorastudio.repository.FolderTemplateRepository.FolderTemplate;
import com.lensora.lensorastudio.repository.ProjectRepository;
import com.lensora.lensorastudio.services.AppSettings;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NewProjectController implements DialogController 
{

    private static final Logger logger = LoggerFactory.getLogger(NewProjectController.class);

    //------------------------------------ FXML fields --------------------------------------------

    @FXML 
    private RadioButton rbWedding, rbEvent, rbGraduation, rbCustom;

    @FXML 
    private TextField fldProjectNumber, fldClientName, fldClientPhone, 
                            fldClientEmail, fldPackageName, fldTotalAmount, fldAdvanceAmount,
                            fldBalanceAmount, fldProjectPath;

    @FXML 
    private ComboBox<String> cmbStatus;

    @FXML 
    private ComboBox<FolderTemplateRepository.FolderTemplate> cmbFolderTemplate;

    @FXML 
    private DatePicker dpEventDate, dpDueDate;

    @FXML 
    private TextArea fldRemarks;

    @FXML 
    private Label lblTemplatePreview, lblFormError;

    @FXML 
    private Label errProjectNumber, errStatus, errClientName, errEventDate,
                    errTotalAmount, errProjectPath, errClientEmail, errClientPhone;

    @FXML 
    private Button btnCancel, btnCreateProject, btnRegenNumber, btnBrowsePath;

    @FXML 
    private HBox newProjectHeaderBar;

    private ToggleGroup typeGroup;
    private String selectedPrefix = "CUS"; // default
    private List<FolderTemplate> cachedTemplates = new ArrayList<>();
    private List<String> currentTemplateFolders;
    private Consumer<Integer> onProjectCreated;

    // ------------------------------------- Initialisation ----------------------------------------

    @FXML
    public void initialize() 
    {
        logger.info("[NewProjectController] Initializing NewProjectController...");
        setupFolderTemplates(); // Load templates first so they are available for typeGroup
        setupTypeGroup();
        setupStatusCombo();
        setupAutoNumberGeneration();
        setupBalanceCalculation();
        setupTemplatePreview();
        setDefaultValues();
    }

    private void setupFolderTemplates() 
    {
        try 
        {
            // Cache DB templates into memory
            cachedTemplates = FolderTemplateRepository.findAll();

            // Set up custom StringConverter for object rendering
            cmbFolderTemplate.setConverter(new StringConverter<>() {
                @Override
                public String toString(FolderTemplate template) 
                {
                    return (template == null) ? "(None - empty folder)" : template.name();
                }

                @Override
                public FolderTemplate fromString(String string) 
                {
                    return null;
                }
            });

            cmbFolderTemplate.getItems().clear();
            cmbFolderTemplate.getItems().add(null); // Represents "(None - empty folder)"
            cmbFolderTemplate.getItems().addAll(cachedTemplates);

            // Match default setting ID to template object
            int defaultTemplateId = AppSettings.getInstance().getDefaultFolderTemplateId();
            FolderTemplate defaultTemplate = cachedTemplates.stream()
                    .filter(t -> t.id() == defaultTemplateId)
                    .findFirst()
                    .orElse(null);

            cmbFolderTemplate.setValue(defaultTemplate);
        }
        catch (SQLException e) 
        {
            logger.error("Failed to load folder templates", e);
            lblFormError.setText("Could not load folder templates.");
            lblFormError.setVisible(true);
        }
    }

    private void setupTypeGroup()
    {
        // Add toggles into a group, so only one can be selected
        typeGroup = new ToggleGroup();
        rbWedding.setToggleGroup(typeGroup);
        rbEvent.setToggleGroup(typeGroup);
        rbGraduation.setToggleGroup(typeGroup);
        rbCustom.setToggleGroup(typeGroup);

        typeGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            if (newVal == rbWedding) 
            {
                selectedPrefix = "WED";
                selectTemplateByName("Wedding Standard");
            } 
            else if (newVal == rbEvent) 
            {
                selectedPrefix = "EVT";
                selectTemplateByName("Event Standard");
            } 
            else if (newVal == rbGraduation) 
            {
                selectedPrefix = "GRD";
                selectTemplateByName("Graduation Standard");
            } 
            else if (newVal == rbCustom) 
            {
                selectedPrefix = "CUS";
                selectTemplateByName(null);
            }
            generateProjectNumber();
        });

        rbWedding.setSelected(true);
    }

    private void selectTemplateByName(String templateName) 
    {
        if (templateName == null || cachedTemplates == null) 
        {
            cmbFolderTemplate.setValue(null);
            return;
        }

        FolderTemplate match = cachedTemplates.stream()
                .filter(t -> t.name().equalsIgnoreCase(templateName))
                .findFirst()
                .orElse(null);

        cmbFolderTemplate.setValue(match);
    }

    private void setupStatusCombo()
    {
        cmbStatus.getItems().setAll(Project.ALL_STATUSES);
        cmbStatus.setValue(AppSettings.getInstance().getDefaultProjectStatus());
    }

    private void setupAutoNumberGeneration() 
    {
        btnRegenNumber.setOnAction(e -> generateProjectNumber());
        generateProjectNumber();
    }

    private void generateProjectNumber() 
    {
        try 
        {
            int seq = ProjectRepository.nextSequence(selectedPrefix);
            String number = String.format("%s-%04d", selectedPrefix, seq);
            fldProjectNumber.setText(number);
            errProjectNumber.setVisible(false);
        } 
        catch (SQLException e) 
        {
            logger.error("Failed to generate project number", e);
            showFieldError(errProjectNumber, "Could not generate project number.");
        }
    }

    private void setupBalanceCalculation() 
    {
        fldTotalAmount.textProperty().addListener((obs, old, newVal) -> calculateBalance());
        fldAdvanceAmount.textProperty().addListener((obs, old, newVal) -> calculateBalance());
    }

    private void calculateBalance() 
    {
        try 
        {
            BigDecimal total = parseCurrency(fldTotalAmount.getText());
            BigDecimal advance = parseCurrency(fldAdvanceAmount.getText());
            BigDecimal balance = total.subtract(advance);
            fldBalanceAmount.setText(formatCurrency(balance));
        } 
        catch (NumberFormatException e) 
        {
            fldBalanceAmount.setText("0.00");
        }
    }

    private BigDecimal parseCurrency(String text) 
    {
        if (text == null || text.trim().isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(text.replaceAll("[^\\d.]", ""));
    }

    private String formatCurrency(BigDecimal value) 
    {
        return String.format("%.2f", value);
    }

    private void setupTemplatePreview() 
    {
        cmbFolderTemplate.valueProperty().addListener((obs, old, selectedTemplate) -> {
            // Clear current folders to prevent state leak on errors or deselection
            currentTemplateFolders = null;

            if (selectedTemplate == null) 
            {
                lblTemplatePreview.setText("No template selected");
                return;
            }

            try 
            {
                List<String> folders = FolderTemplateRepository.getFolderNames(selectedTemplate.id());
                currentTemplateFolders = folders;
                lblTemplatePreview.setText(String.join("\n", folders));
            } 
            catch (SQLException e) 
            {
                logger.error("Failed to load template folders for ID: {}", selectedTemplate.id(), e);
                lblTemplatePreview.setText("Error loading template preview");
            }
        });
    }

    private void setDefaultValues() 
    {
        dpEventDate.setValue(LocalDate.now().plusDays(7));
        fldProjectPath.setEditable(false);
        fldBalanceAmount.setEditable(false);

        // Set default root from settings
        String defaultRoot = AppSettings.getInstance().getDefaultProjectRoot();
        if (defaultRoot != null && !defaultRoot.isEmpty()) fldProjectPath.setText(defaultRoot);
    }

    // --------------------------------Event Handlers ------------------------------------------

    @FXML
    private void onBrowseProjectPath() 
    {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Project Root Folder");
        Stage stage = (Stage) fldProjectPath.getScene().getWindow();
        File selected = chooser.showDialog(stage);
        if (selected != null) 
        {
            fldProjectPath.setText(selected.getAbsolutePath());
            errProjectPath.setVisible(false);
        }
    }

    @FXML
    private void onCancel() 
    {
        closeDialog();
    }

    @FXML
    private void onCreateProject() 
    {
        if (!validateForm()) return;

        // Disable the Create button to prevent double-click
        btnCreateProject.setDisable(true);
        btnCreateProject.setText("Creating...");

        // Build project
        Project project = buildProjectFromForm();

        // Get root path and project number
        String root = fldProjectPath.getText().trim();
        String projectNumber = fldProjectNumber.getText().trim();

        // Build the actual project folder path
        String projectFolderPath = root + File.separator + projectNumber;
        project.setProjectPath(projectFolderPath);

        // Run the heavy operations in a background thread
        Task<Integer> createTask = new Task<>() {
            @Override
            protected Integer call() throws Exception 
            {
                // Insert into database
                int projectId = ProjectRepository.insert(project);
                if (projectId < 0) 
                {
                    throw new SQLException("Failed to save project to database.");
                }

                // Create folders inside the project folder
                createProjectFolders(projectFolderPath, currentTemplateFolders);
                return projectId;
            }
        };

        createTask.setOnSucceeded(event -> {
            int projectId = createTask.getValue();
            logger.info("Project created with ID: {}", projectId);

            // Notify MainController on the FX thread
            if (onProjectCreated != null) 
            {
                onProjectCreated.accept(projectId);
            }
            closeDialog();
        });

        createTask.setOnFailed(event -> {
            Throwable ex = createTask.getException();
            logger.error("Error creating project", ex);
            btnCreateProject.setDisable(false);
            btnCreateProject.setText("Create Project");
            showFormError("Error creating project: " + ex.getMessage());
        });

        // Start the background thread
        new Thread(createTask).start();
    } 


    @FXML
    private void onRegenNumber() 
    {
        generateProjectNumber();
    }

    // ─── Validation ──────────────────────────────────────────────────────────

    private boolean validateForm() 
    {
        boolean valid = true;
        clearAllErrors();

        if (fldProjectNumber.getText() == null || fldProjectNumber.getText().trim().isEmpty()) 
        {
            showFieldError(errProjectNumber, "Project number is required.");
            valid = false;
        }
        if (cmbStatus.getValue() == null) 
        {
            showFieldError(errStatus, "Status is required.");
            valid = false;
        }
        if (fldClientName.getText() == null || fldClientName.getText().trim().isEmpty()) 
        {
            showFieldError(errClientName, "Client name is required.");
            valid = false;
        }
        if (dpEventDate.getValue() == null) 
        {
            showFieldError(errEventDate, "Event date is required.");
            valid = false;
        }
        if (fldProjectPath.getText() == null || fldProjectPath.getText().trim().isEmpty()) 
        {
            showFieldError(errProjectPath, "Project root folder is required.");
            valid = false;
        }

        try 
        {
            parseCurrency(fldTotalAmount.getText());
        } 
        catch (NumberFormatException e) 
        {
            showFieldError(errTotalAmount, "Invalid total amount.");
            valid = false;
        }

        if (fldAdvanceAmount.getText() != null && !fldAdvanceAmount.getText().trim().isEmpty()) 
        {
            try 
            {
                parseCurrency(fldAdvanceAmount.getText());
            } 
            catch (NumberFormatException e) 
            {
                showFieldError(errTotalAmount, "Invalid advance amount.");
                valid = false;
            }
        }

        if (!valid) 
        {
            lblFormError.setText("Please fix the errors above.");
            lblFormError.setVisible(true);
        }

        // Validate email format
        String email = fldClientEmail.getText().trim();
        if (!email.isEmpty() && !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) 
        {
            showFieldError(errClientEmail, "Invalid email address.");
            valid = false;
        }

        // Validate phone
        String phone = fldClientPhone.getText().trim();
        if (!phone.isEmpty() && !phone.matches("^[+0-9\\s()\\-]{7,20}$")) 
        {
            showFieldError(errClientPhone, "Invalid phone number.");
            valid = false;
        }
        return valid;
    }

    private void showFieldError(Label errorLabel, String message) 
    {
        if (errorLabel == null) return;
        if (message == null || message.isBlank())
        {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            return;
        }

        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearAllErrors() 
    {
        errProjectNumber.setVisible(false);
        errStatus.setVisible(false);
        errClientName.setVisible(false);
        errEventDate.setVisible(false);
        errTotalAmount.setVisible(false);
        errProjectPath.setVisible(false);
        errClientEmail.setVisible(false);
        errClientPhone.setVisible(false);
        lblFormError.setVisible(false);
    }

    private void showFormError(String message) 
    {
        lblFormError.setText(message);
        lblFormError.setVisible(true);
    }

    // ------------------------------ Project Building ---------------------------------------

    private Project buildProjectFromForm() 
    {
        Project p = new Project();
        p.setProjectNumber(fldProjectNumber.getText().trim());
        p.setClientName(fldClientName.getText().trim());
        p.setClientPhone(fldClientPhone.getText().trim());
        p.setClientEmail(fldClientEmail.getText().trim());
        p.setEventType(getSelectedEventType());
        p.setEventDate(dpEventDate.getValue());
        p.setDueDate(dpDueDate.getValue());
        p.setProjectStatus(cmbStatus.getValue());
        p.setProjectPath(fldProjectPath.getText().trim());
        p.setPackageName(fldPackageName.getText().trim());
        p.setTotalAmount(parseCurrency(fldTotalAmount.getText()));
        p.setAdvanceAmount(parseCurrency(fldAdvanceAmount.getText()));
        p.setBalanceAmount(parseCurrency(fldBalanceAmount.getText()));
        p.setRemarks(fldRemarks.getText().trim());
        return p;
    }

    private String getSelectedEventType()
    {
        if (rbWedding.isSelected()) return "Wedding";
        if (rbEvent.isSelected()) return "Event";
        if (rbGraduation.isSelected()) return "Graduation";
        return "Custom";
    }

    // ------------------------------- Folder Creation ---------------------------------------

    private void createProjectFolders(String rootPath, List<String> subfolders) throws IOException
    {
        Path root = Paths.get(rootPath);
        if (!Files.exists(root)) 
        {
            Files.createDirectories(root);
        }
        if (subfolders != null && !subfolders.isEmpty())
        {
            for (String folder : subfolders) 
            {
                Path sub = root.resolve(folder);
                Files.createDirectories(sub);
            }
        }
    }

    // ------------------------------- Dialog Management ------------------------------------

    private void closeDialog() 
    {
        Stage stage = (Stage) fldProjectNumber.getScene().getWindow();
        if (stage != null) stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    public void setOnProjectCreated(Consumer<Integer> callback) 
    {
        this.onProjectCreated = callback;
    }
}