package com.lensora.lensorastudio.feature.explorer.controller;

import com.lensora.lensorastudio.feature.explorer.control.FileListingManager;
import com.lensora.lensorastudio.feature.explorer.control.FileManager;
import com.lensora.lensorastudio.feature.project.model.Collection;
import com.lensora.lensorastudio.feature.project.repository.CollectionRepository;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

import org.kordamp.ikonli.javafx.FontIcon;
import org.snapfx.SnapFX;

public class FileExplorerController
{
    @FXML private SplitPane fileSplitPane;
    @FXML private TreeView<File> folderTree;
    @FXML private TableView<File> fileTable;
    @FXML private TableColumn<File, String> colFileName, colFileType, colFileSize, colFileDimensions, colFileModified;
    @FXML private Label lblCurrentFolder, lblFileCount, lblSelectedFileCount, lblFolderHeader;
    @FXML private HBox breadcrumbContainer;
    @FXML private Button btnFolderBack, btnFolderForward, btnRefreshFileList;
    @FXML private TextField fileSearchField;
    @FXML private ToggleButton btnDetails, btnList, btnIcons, btnThumbnails;
    @FXML private ListView<File> fileListView;
    @FXML private StackPane iconGridHost;

    @FXML private TabPane leftPanelTabs;
    @FXML private Tab tabFolders;
    @FXML private Tab tabCollections;
    @FXML private ListView<Collection> collectionsListView;
    @FXML private Button btnNewCollection;

    private FileManager fileManager;

    @FXML
    public void initialize()
    {
        setupCollectionsTab();
        setupTabSwitchListener();
        setupKeyboardShortcuts();
    }

    /**
     * Finishes constructing the FileManager once status bar progress widgets exist.
     */
    public void wireProgressUi(HBox progressContainer, ProgressBar progressBar,
                                Label progressLabel, Label progressSpeedLabel, Label progressEtaLabel)
    {
        fileManager = new FileManager(
                folderTree, 
                fileTable,
                colFileName, 
                colFileType, 
                colFileSize, 
                colFileDimensions, 
                colFileModified,
                lblCurrentFolder, 
                lblFileCount,
                lblFolderHeader,
                progressContainer, 
                progressBar, 
                progressLabel, 
                progressSpeedLabel,
                progressEtaLabel,
                breadcrumbContainer, 
                btnFolderBack, 
                btnFolderForward,
                btnRefreshFileList,
                fileSearchField,
                btnDetails, 
                btnList, 
                btnIcons, 
                btnThumbnails,
                fileListView,
                iconGridHost
        );
    }

    private void setupTabSwitchListener() 
    {
        leftPanelTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == null) return;

            if (newTab == tabFolders)
            {
                TreeItem<File> selectedFolder = folderTree.getSelectionModel().getSelectedItem();
                if (selectedFolder != null && selectedFolder.getValue() != null) 
                {
                    fileManager.loadFolder(selectedFolder.getValue());
                }
            } 
            else if (newTab == tabCollections) 
            {
                Collection selectedCollection = collectionsListView.getSelectionModel().getSelectedItem();
                if (selectedCollection != null)
                {
                    loadCollectionFiles(selectedCollection);
                }
            }
        });
    }

    private void setupKeyboardShortcuts()
    {
        // Focus search input on Alt + F
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

        // Select all text on mouse click/focus in search field
        fileSearchField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused)
            {
                Platform.runLater(fileSearchField::selectAll);
            }
        });

        // Refresh file list & tree on F5
        btnRefreshFileList.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null)
            {
                oldScene.getAccelerators().remove(new KeyCodeCombination(KeyCode.F5));
            }
            if (newScene != null)
            {
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.F5),
                        () -> { if (btnRefreshFileList != null) btnRefreshFileList.fire(); });
            }
        });
    }

    // ─── Collections Tab ───────────────────────────────────────────────────

    private void setupCollectionsTab()
    {
        collectionsListView.setCellFactory(lv -> {
            ListCell<Collection> cell = new ListCell<>() {
                @Override 
                protected void updateItem(Collection c, boolean empty) 
                {
                    super.updateItem(c, empty);
                    
                    if (empty || c == null) 
                    {
                        setText(null);
                        setGraphic(null);
                    } 
                    else 
                    {
                        setText(c.getName());
                        if (c.getIcon() != null && !c.getIcon().isBlank()) 
                        {
                            try 
                            {
                                FontIcon iconNode = new FontIcon(c.getIcon());
                                iconNode.getStyleClass().add("icon-size-14");
                                setGraphic(iconNode);
                            } 
                            catch (Exception e)
                            {
                                setGraphic(new FontIcon("fas-folder"));
                            }
                        }
                        else 
                        {
                            setGraphic(null);
                        }
                    }
                }
            };

            // Context menu for collections (Delete / Rename)
            ContextMenu contextMenu = new ContextMenu();
            MenuItem itemDelete = new MenuItem("Delete Collection");
            itemDelete.setOnAction(e -> deleteSelectedCollection(cell.getItem()));
            contextMenu.getItems().add(itemDelete);

            cell.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                if (isEmpty) {
                    cell.setContextMenu(null);
                } else {
                    cell.setContextMenu(contextMenu);
                }
            });

            return cell;
        });

        collectionsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) loadCollectionFiles(selected);
        });

        btnNewCollection.setOnAction(e -> createCollection());
        refreshCollections();
    }

    public void refreshCollections()
    {
        try
        {
            collectionsListView.setItems(FXCollections.observableArrayList(CollectionRepository.findAll()));
        }
        catch (SQLException e)
        {
            ErrorHandler.show(null, "Failed to load collections", e);
        }
    }

    private void loadCollectionFiles(Collection collection)
    {
        try
        {
            List<String> paths = CollectionRepository.resolveFilePaths(collection);
            List<File> files = paths.stream().map(File::new).filter(File::exists).toList();
            if (fileManager != null) fileManager.showVirtualFileSet(files);
        }
        catch (SQLException e)
        {
            ErrorHandler.show(null, "Failed to load collection", e);
        }
    }

    private void createCollection()
    {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Collection");
        dialog.setHeaderText(null);
        dialog.setContentText("Collection name:");
        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.isBlank()) return;
            try
            {
                CollectionRepository.insertManual(name.trim(), "fas-folder");
                refreshCollections();
            }
            catch (SQLException e)
            {
                ErrorHandler.show(null, "Failed to create collection", e);
            }
        });
    }

    private void deleteSelectedCollection(Collection selected)
    {
        if (selected == null || selected.isBuiltin()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Collection");
        confirm.setHeaderText(null);
        confirm.setContentText("Delete collection \"" + selected.getName() + "\"? Files themselves are not affected.");
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;
            try
            {
                CollectionRepository.delete(selected.getCollectionId());
                refreshCollections();
            }
            catch (SQLException e)
            {
                ErrorHandler.show(null, "Failed to delete collection", e);
            }
        });
    }

    // ─── Public API & Delegation ───────────────────────────────────────────

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
        return fileManager != null ? fileManager.getCurrentFolderRelativePath() : "";
    }

    public void restoreLastFolder(String relativePath)
    {
        if (fileManager != null) fileManager.expandAndSelectRelativePath(relativePath);
    }

    public void setupCopyPasteShortcuts(Scene scene)
    {
        if (fileManager != null) fileManager.setupCopyPasteShortcuts(scene);
    }

    public void setOnNavigationPersisted(Consumer<File> callback)
    {
        if (fileManager != null) fileManager.setOnNavigationPersisted(callback);
    }

    public void updateSearchDebounce() 
    {
        if (fileManager != null) 
        {
            FileListingManager flm = fileManager.getFileListingManager();
            if (flm != null) 
            {
                flm.updateSearchDebounce();
            }
        }
    }

    public void goBack()    { if (fileManager != null) fileManager.goBack(); }
    public void goForward() { if (fileManager != null) fileManager.goForward(); }

    public void setSnapFX(SnapFX snapFX) 
    {
        if (fileManager != null) fileManager.setSnapFX(snapFX);
    }

    public void shutdown()
    {
        if (fileManager != null) fileManager.shutdown();
    }
}