package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.managers.FileManager;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.File;

import org.snapfx.SnapFX;

public class FileExplorerController
{
    @FXML private SplitPane fileSplitPane;
    @FXML private TreeView<File> folderTree;
    @FXML private TableView<File> fileTable;
    @FXML private TableColumn<File, String> colFileName, colFileType, colFileSize, colFileDimensions, colFileModified;
    @FXML private Label lblCurrentFolder, lblFileCount, lblFolderHeader;
    @FXML private HBox breadcrumbContainer;
    @FXML private Button btnFolderBack, btnFolderForward;
    @FXML private TextField fileSearchField;
    @FXML private ToggleButton btnDetails, btnList, btnIcons, btnThumbnails;
    @FXML private ListView<File> fileListView;
    @FXML private ScrollPane iconScrollPane;
    @FXML private FlowPane iconFlowPane;
    @FXML private MenuItem ctxFileOpen, ctxFileRename, ctxFileCopy, ctxFileCut, ctxFileMove, ctxFileDelete, ctxFileShowInExplorer, ctxFileProperties;

    // NOTE: FileManager still owns a progress bar/status area for copy-paste.
    // Those live in the status bar module now, so we pass in references
    // obtained from StatusBarController via wireProgressUi(...).
    private FileManager fileManager;
    private final ToggleGroup viewToggleGroup = new ToggleGroup();

    @FXML
    public void initialize()
    {
        // fileManager is fully constructed in wireProgressUi() once the
        // status-bar module (which owns the copy-progress widgets) is available.
        setupKeyboardShortcuts();
    }

    /**
     * Finishes constructing the FileManager once the status bar's progress
     * widgets exist. Called by MainController right after both this
     * controller and StatusBarController have been loaded.
     */
    public void wireProgressUi(HBox progressContainer, ProgressBar progressBar,
                                Label progressLabel, Label progressSpeedLabel, Label progressEtaLabel)
    {
        fileManager = new FileManager(
                folderTree, fileTable,
                colFileName, colFileType, colFileSize, colFileDimensions, colFileModified,
                lblCurrentFolder, lblFileCount, lblFolderHeader,
                progressContainer, progressBar, progressLabel, progressSpeedLabel, progressEtaLabel,
                ctxFileOpen, ctxFileRename, ctxFileCopy, ctxFileCut, ctxFileMove, ctxFileDelete, ctxFileShowInExplorer,
                ctxFileProperties, breadcrumbContainer, btnFolderBack, btnFolderForward, fileSearchField,
                viewToggleGroup, btnDetails, btnList, btnIcons, btnThumbnails,
                fileListView, iconScrollPane, iconFlowPane
        );
    }

        private void setupKeyboardShortcuts()
        {
            if (ctxFileCopy != null) 
                ctxFileCopy.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
            if (ctxFileDelete != null)
                ctxFileDelete.setAccelerator(new KeyCodeCombination(KeyCode.DELETE, KeyCombination.SHIFT_DOWN));
            if (ctxFileMove != null)
                ctxFileMove.setAccelerator(new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN));
            if (ctxFileCut != null)
                ctxFileCut.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));
        }


    public void setStage(Stage stage)
    {
        if (fileManager != null) fileManager.setStage(stage);
    }

    public FileManager getFileManager()
    {
        return fileManager;
    }

    public void loadProjectPath(String path)
    {
        if (fileManager != null) fileManager.loadProjectPath(path);
    }

    public void setupCopyPasteShortcuts(javafx.scene.Scene scene)
    {
        if (fileManager != null) fileManager.setupCopyPasteShortcuts(scene);
    }

    public void goBack()    { if (fileManager != null) fileManager.goBack(); }
    public void goForward() { if (fileManager != null) fileManager.goForward(); }

    public void setSnapFX(SnapFX snapFX) 
    {
        if (fileManager != null) fileManager.setSnapFX(snapFX);
    }
}