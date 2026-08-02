package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.managers.FileManager;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.function.Consumer;

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
    @FXML private MenuItem ctxFileOpen, ctxFileRename, ctxFileCopy, ctxFileCut, ctxFileMove, ctxFileDelete, ctxFileShowInExplorer, ctxFileProperties, ctxOpenInAnotherWindow;
    @FXML private Menu ctxOpenWithMenu;

    // NOTE: FileManager still owns a progress bar/status area for copy-paste.
    // Those live in the status bar module now, so we pass in references
    // obtained from StatusBarController via wireProgressUi(...).
    private FileManager fileManager;
    private final ToggleGroup viewToggleGroup = new ToggleGroup();

    private KeyCombination fileCopyAccel                = new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN);
    private KeyCombination fileDeleteAccel              = new KeyCodeCombination(KeyCode.DELETE, KeyCombination.SHIFT_DOWN);
    private KeyCombination fileMoveAccel                = new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN);
    private KeyCombination fileCutAccel                 = new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN);
    private KeyCombination fileRenameAccel              = new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN);
    private KeyCombination fileOpenExplorerAccel        = new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN);
    private KeyCombination filePropertiesAccel          = new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN);

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
        BooleanBinding multiSelectBinding = Bindings.size(fileTable.getSelectionModel().getSelectedItems()).isNotEqualTo(1);
        fileManager = new FileManager
        (
                folderTree, fileTable,
                colFileName, colFileType, colFileSize, colFileDimensions, colFileModified,
                lblCurrentFolder, lblFileCount, lblFolderHeader,
                progressContainer, progressBar, progressLabel, progressSpeedLabel, progressEtaLabel,
                ctxFileOpen, ctxOpenWithMenu, ctxFileRename, ctxFileCopy, ctxFileCut, ctxFileMove, ctxFileDelete, ctxFileShowInExplorer,
                ctxFileProperties, ctxOpenInAnotherWindow, breadcrumbContainer, btnFolderBack, btnFolderForward, fileSearchField,
                viewToggleGroup, btnDetails, btnList, btnIcons, btnThumbnails,
                fileListView, iconScrollPane, iconFlowPane,  multiSelectBinding
        );

        // Listen for selection changes
        multiSelectBinding.addListener((obs, oldVal, isMulti) -> {
            updateKeyboardAccelerators(isMulti);
        });

        // Initial key shortcut 
        updateKeyboardAccelerators(multiSelectBinding.get());
    }

    private void setupKeyboardShortcuts()
    {
        if (ctxFileCopy != null)
            ctxFileCopy.setAccelerator(fileCopyAccel);
        if (ctxFileDelete != null)
            ctxFileDelete.setAccelerator(fileDeleteAccel);
        if (ctxFileMove != null)
            ctxFileMove.setAccelerator(fileMoveAccel);
        if (ctxFileCut != null)
            ctxFileCut.setAccelerator(fileCutAccel);

        fileSearchField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null)
            {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                    if (e.isAltDown() && e.getCode() == KeyCode.F)
                    {
                        fileSearchField.requestFocus();
                        fileSearchField.selectAll();
                        e.consume();
                    }
                });
            }
        });
    }

    private void updateKeyboardAccelerators(boolean multipleSelected) 
    {
        ctxFileRename.setAccelerator(multipleSelected ? null : fileRenameAccel);
        ctxFileShowInExplorer.setAccelerator(multipleSelected ? null : fileOpenExplorerAccel);
        ctxFileProperties.setAccelerator(multipleSelected ? null : filePropertiesAccel);
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

    public String getCurrentFolderRelativePath()
    {
        return fileManager.getCurrentFolderRelativePath();
    }

    public void restoreLastFolder(String relativePath)
    {
        fileManager.expandAndSelectRelativePath(relativePath);
    }

    public void setupCopyPasteShortcuts(javafx.scene.Scene scene)
    {
        if (fileManager != null) fileManager.setupCopyPasteShortcuts(scene);
    }

    public void setOnNavigationPersisted(Consumer<File> callback)
    {
        fileManager.setOnNavigationPersisted(callback);
    }

    public void goBack()    { if (fileManager != null) fileManager.goBack(); }
    public void goForward() { if (fileManager != null) fileManager.goForward(); }

    public void setSnapFX(SnapFX snapFX) 
    {
        if (fileManager != null) fileManager.setSnapFX(snapFX);
    }
}