package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.util.ErrorHandler;
import com.lensora.lensorastudio.viewmodel.ProjectsViewModel;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public class ProjectDetailsController
{
    // Overview tab
    @FXML private TextField detProjectNumber, detClientName, detClientPhone, detClientEmail,
            detEventType, detEventDate, detDueDate, detStatus, detPackage, detProjectPath,
            detTotalAmount, detAdvanceAmount, detBalanceAmount;
    @FXML private TextArea detRemarks;
    @FXML private HBox progressStepper;
    @FXML private Button btnDetailEdit, btnDetailOpenFolder, btnDetailArchive;

    // Notes tab
    @FXML private VBox notesContainer;
    @FXML private Button btnAddNote;

    // Reminders tab (project-scoped)
    @FXML private TableView<Object> projectReminderTable;
    @FXML private TableColumn<Object, String> colProjRemDate, colProjRemTitle, colProjRemDone;
    @FXML private Button btnAddReminder;

    // Payments tab
    @FXML private TableView<Object> paymentTable;
    @FXML private TableColumn<Object, String> colPayDate, colPayAmount, colPayMethod, colPayRef;
    @FXML private Button btnAddPayment;

    private ProjectsViewModel viewModel;
    private Consumer<String> onProjectPathChanged;

    public void bind(ProjectsViewModel viewModel)
    {
        this.viewModel = viewModel;

        viewModel.selectedProjectProperty().addListener((obs, oldVal, newVal) -> loadProject(newVal));
        loadProject(viewModel.getSelectedProject());

        btnDetailOpenFolder.setOnAction(e -> openProjectFolder());

        // Not implemented yet — wired so the buttons don't silently do nothing.
        btnDetailEdit.setOnAction(e -> { /* TODO: open edit dialog */ });
        btnDetailArchive.setOnAction(e -> { /* TODO: archive project */ });
        btnAddNote.setOnAction(e -> { /* TODO: append a note card to notesContainer */ });
        btnAddReminder.setOnAction(e -> { /* TODO: insert into reminder table */ });
        btnAddPayment.setOnAction(e -> { /* TODO: insert into payment table */ });

        projectReminderTable.setPlaceholder(new Label("No reminders for this project."));
        paymentTable.setPlaceholder(new Label("No payments recorded yet."));
    }

    public void setOnProjectPathChanged(Consumer<String> callback) { this.onProjectPathChanged = callback; }

    private void loadProject(Project project)
    {
        if (project == null) { clearFields(); return; }

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

        progressStepper.getChildren().clear();
        Label statusLabel = new Label("Status: " + project.getProjectStatus());
        statusLabel.setStyle("-fx-font-weight: bold;");
        progressStepper.getChildren().add(statusLabel);

        if (onProjectPathChanged != null) onProjectPathChanged.accept(project.getProjectPath());
    }

    private void clearFields()
    {
        for (TextField tf : new TextField[]{detProjectNumber, detClientName, detClientPhone, detClientEmail,
                detEventType, detEventDate, detDueDate, detStatus, detPackage, detProjectPath,
                detTotalAmount, detAdvanceAmount, detBalanceAmount})
        {
            tf.setText("");
        }
        detRemarks.setText("");
        progressStepper.getChildren().clear();
        notesContainer.getChildren().clear();
    }

    private void openProjectFolder()
    {
        Project current = viewModel.getSelectedProject();
        if (current == null || current.getProjectPath() == null) return;
        try
        {
            if (Desktop.isDesktopSupported())
            {
                Desktop.getDesktop().open(new File(current.getProjectPath()));
            }
        }
        catch (IOException ex)
        {
            ErrorHandler.show(null, "Could not open folder", ex);
        }
    }
}