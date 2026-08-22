package com.lensora.lensorastudio.feature.explorer.control;

import com.lensora.lensorastudio.core.config.AppSettings;
import com.lensora.lensorastudio.feature.explorer.view.FileDimensionService;
import com.lensora.lensorastudio.feature.explorer.view.FileListViewManager;
import com.lensora.lensorastudio.feature.explorer.view.FileTableViewManager;
import com.lensora.lensorastudio.feature.explorer.view.FileViewSwitcher;
import com.lensora.lensorastudio.feature.viewer.ImageViewerWindowService;
import com.lensora.lensorastudio.media.service.ImageValidator;
import com.lensora.lensorastudio.ui.components.VirtualizedThumbnailGrid;
import com.lensora.lensorastudio.util.ClipboardFormats;
import com.lensora.lensorastudio.util.ExternalAppLauncher;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.snapfx.SnapFX;

/**
 * Owns the file listing: the details TableView, ListView, and icon/thumbnail
 */
public class FileListingManager
{
    private final FileTableViewManager tableManager;
    private final FileListViewManager listManager;
    private final VirtualizedThumbnailGrid iconGrid = new VirtualizedThumbnailGrid();
    private final FileViewSwitcher viewSwitcher;
    private final FileDimensionService dimensionService;

    private final TextField fileSearchField;
    private final Label lblCurrentFolder, lblFileCount;

    private ObservableList<File> currentFiles = FXCollections.observableArrayList();
    private final ObjectProperty<File> selectedFile = new SimpleObjectProperty<>();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(100));

    private final IntegerProperty selectedCount = new SimpleIntegerProperty(0);
    
    private BiConsumer<List<File>, Boolean> onExternalFilesDropped;
    private Runnable refreshCallback;
    private Consumer<File> customDoubleClickHandler;

    private File currentFolder;
    private Task<Void> searchTask;
    private SnapFX snapFX;

    public void setSnapFX(SnapFX snapFX) { this.snapFX = snapFX; }

    public FileListingManager(
                                TableView<File> fileTable,
                                TableColumn<File, String> colName, TableColumn<File, String> colType,
                                TableColumn<File, String> colSize, TableColumn<File, String> colDimensions,
                                TableColumn<File, String> colModified,
                                ListView<File> fileListView,
                                StackPane iconGridHost,
                                Label lblCurrentFolder, Label lblFileCount, TextField fileSearchField,
                                ToggleButton btnDetails, ToggleButton btnList, ToggleButton btnIcons, ToggleButton btnThumbnails,
                                Button btnRefreshFileList
                            )
    {
        this.dimensionService = new FileDimensionService();
        this.tableManager = new FileTableViewManager(fileTable, colName, colType, colSize, colDimensions, colModified, dimensionService);
        this.listManager = new FileListViewManager(fileListView);

        // The grid's internal ListView was being built but never attached to
        // the scene graph - iconGridHost (the actual FXML StackPane sibling
        // of fileTable/fileListView) had no children, so nothing could ever
        // render there regardless of visibility toggling.
        iconGridHost.getChildren().setAll(iconGrid.getNode());

        // FileViewSwitcher must control iconGridHost's visible/managed state
        // (the node that's actually positioned in the FXML StackPane), not
        // iconGrid.getNode() directly - the inner ListView being visible/
        // managed means nothing if its OUTER container is still hidden.
        this.viewSwitcher = new FileViewSwitcher(fileTable, fileListView, iconGridHost, btnDetails, btnList, btnIcons, btnThumbnails);

        this.lblCurrentFolder = lblCurrentFolder;
        this.lblFileCount = lblFileCount;
        this.fileSearchField = fileSearchField;

        if (btnRefreshFileList != null)
        {
            btnRefreshFileList.setOnAction(e -> {
                if (refreshCallback != null) refreshCallback.run();
            });
        }

        // Set default double click action
        setOnDoubleClick(this::handleDoubleClickOpen);

        viewSwitcher.setOnViewChanged(mode -> {
            refreshActiveView();
            selectedFile.set(null);
            refreshSelectionState();
        });

        setupSelectionTracking();
        setupDropTargets();
        setupSearch();
    }

    public void setOnFilesDroppedIntoCurrentFolder(BiConsumer<List<File>, Boolean> callback)
    {
        this.onExternalFilesDropped = callback;
    }

    public void setRefreshCallback(Runnable callback)
    {
        this.refreshCallback = callback;
    }

    public void attachSharedContextMenu(ContextMenu menu)
    {
        tableManager.attachSharedContextMenu(menu);
        listManager.attachSharedContextMenu(menu);
        iconGrid.attachSharedContextMenu(menu);
    }

    public void setOnDoubleClick(Consumer<File> handler)
    {
        this.customDoubleClickHandler = handler;
        tableManager.setOnDoubleClick(handler);
        listManager.setOnDoubleClick(handler);
        iconGrid.setOnDoubleClick(handler);
    }

    public ReadOnlyIntegerProperty selectedCountProperty() 
    { 
        return selectedCount;
    }

    public BooleanBinding moreThanOneSelectedBinding() 
    {
        return Bindings.greaterThan(selectedCount, 1);
    }

    public void shutdownDimensionExecutor()
    {
        dimensionService.shutdownDimensionExecutor();
    }

    private void setupSelectionTracking()
    {
        // TableView selection
        tableManager.getNode().getSelectionModel().selectedItemProperty()
                .addListener((obs, old, val) -> {
                    if (viewSwitcher.getCurrentMode() == FileViewSwitcher.ViewMode.DETAILS) 
                    {
                        selectedFile.set(val);
                        refreshSelectionState();
                    }
                });

        tableManager.getNode().getSelectionModel().getSelectedItems()
                .addListener((ListChangeListener<File>) change -> {
                    if (viewSwitcher.getCurrentMode() == FileViewSwitcher.ViewMode.DETAILS) 
                    {
                        refreshSelectionState();
                    }
                });

        // ListView selection
        listManager.getNode().getSelectionModel().selectedItemProperty()
                .addListener((obs, old, val) -> {
                    if (viewSwitcher.getCurrentMode() == FileViewSwitcher.ViewMode.LIST) 
                    {
                        selectedFile.set(val);
                        refreshSelectionState();
                    }
                });

        listManager.getNode().getSelectionModel().getSelectedItems()
                .addListener((ListChangeListener<File>) change -> {
                    if (viewSwitcher.getCurrentMode() == FileViewSwitcher.ViewMode.LIST) 
                    {
                        refreshSelectionState();
                    }
                });

        // Thumbnail/Icon grid selection
        iconGrid.setOnSelectionChanged(file -> {
            if (viewSwitcher.getCurrentMode() == FileViewSwitcher.ViewMode.ICONS
                    || viewSwitcher.getCurrentMode() == FileViewSwitcher.ViewMode.THUMBNAILS) 
            {
                selectedFile.set(file);
                refreshSelectionState();
            }
        });
    }

    private void setupDropTargets()
    {
        setupDropTarget(tableManager.getNode());
        setupDropTarget(listManager.getNode());
        setupDropTarget(iconGrid.getNode());
    }

    private void setupDropTarget(Node target)
    {
        target.setOnDragOver(event -> {
            if (event.getGestureSource() != target && event.getDragboard().hasFiles())
            {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        target.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles())
            {
                boolean isInternal = Boolean.TRUE.equals(db.getContent(ClipboardFormats.INTERNAL_DRAG));

                if (!isInternal && onExternalFilesDropped != null)
                {
                    onExternalFilesDropped.accept(db.getFiles(), false);
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void handleDoubleClickOpen(File file)
    {
        if (file == null || file.isDirectory()) return;

        if (ImageValidator.isJavaFXLoadable(file))
        {
            ImageViewerWindowService.getInstance().openImages(List.of(file));
        }
        else
        {
            ExternalAppLauncher.openWithSystemDefault(file);
        }
    }

    /** Updates the unified selection count property. */
    private void updateSelectedCount() 
    {
        selectedCount.set(getSelectedFiles().size());
    }

    private void updateFileCountLabel()
    {
        int total = currentFiles.size();
        int selected = selectedCount.get();

        if (total == 0)
        {
            lblFileCount.setText("0 files");
        }
        else if (selected == 0)
        {
            lblFileCount.setText(total + (total == 1 ? " file" : " files"));
        }
        else
        {
            lblFileCount.setText(String.format("%d %s (%d selected)", 
                total, (total == 1 ? "file" : "files"), selected));
        }
    }

    /** Single source of truth for both selectedCount and the file-count label — always called together, in order. */
    private void refreshSelectionState()
    {
        updateSelectedCount();     // update the cached property FIRST
        updateFileCountLabel();    // THEN render the label (and any debug logging) from the now-current value
    }

    public ObjectProperty<File> selectedFileProperty()
    {
        return selectedFile;
    }

    // ─── Loading ────────────────────────────────────────────────────────────

    public void loadFolder(File folder)
    {
        dimensionService.clear();
        this.currentFolder = folder;

        if (folder == null || !folder.isDirectory())
        {
            currentFiles.clear();
            lblCurrentFolder.setText("");
            refreshSelectionState();
            return;
        }

        File[] files = folder.listFiles(File::isFile);
        if (files == null) files = new File[0];
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        currentFiles = FXCollections.observableArrayList(files);
        applyToActiveView();

        lblCurrentFolder.setText(folder.getName());
        refreshSelectionState();

        if (!fileSearchField.getText().isEmpty()) fileSearchField.clear();
    }

    // API to load custom file lists
    public void showVirtualFileSet(List<File> files)
    {
        dimensionService.clear();
        currentFolder = null;
        selectedFile.set(null);
        currentFiles = FXCollections.observableArrayList(files);
        applyToActiveView();
        lblCurrentFolder.setText("Collection");
        refreshSelectionState();
    }

    public void refresh() { if (currentFolder != null) loadFolder(currentFolder); }

    private void applyToActiveView()
    {
        refreshActiveView();
    }

    private void refreshActiveView()
    {
        System.out.println("REFRESH ACTIVE VIEW: "+ viewSwitcher.getCurrentMode());
        switch (viewSwitcher.getCurrentMode())
        {
            case LIST -> listManager.setItems(currentFiles);
            case ICONS -> { iconGrid.setThumbnailsEnabled(false); iconGrid.setFiles(currentFiles); }
            case THUMBNAILS -> { iconGrid.setThumbnailsEnabled(true); iconGrid.setFiles(currentFiles); }
            default -> tableManager.setItems(currentFiles);
        }
    }

    public File getSelectedFile()
    {
        List<File> selected = getSelectedFiles();
        return selected.isEmpty() ? null : selected.get(0);
    }

    public List<File> getSelectedFiles()
    {
        return switch (viewSwitcher.getCurrentMode())
        {
            case LIST -> listManager.getSelectedFiles();
            case ICONS, THUMBNAILS -> iconGrid.getSelectedFiles();
            default -> tableManager.getSelectedFiles();
        };
    }

    public boolean isFocused()
    {
        return switch (viewSwitcher.getCurrentMode())
        {
            case LIST -> listManager.isFocused();
            case ICONS, THUMBNAILS -> iconGrid.isFocused();
            default -> tableManager.isFocused();
        };
    }

    public TableView<File> getFileTable() 
    { 
        return tableManager.getNode(); 
    }

    // ─── Search ─────────────────────────────────────────────────────────────

    private void setupSearch()
    {
        updateSearchDebounce();

        fileSearchField.textProperty().addListener((obs, old, newVal) -> {
            searchDebounce.setOnFinished(e -> runSearch(newVal));
            searchDebounce.playFromStart();
        });
    }

    private void runSearch(String query)
    {
        if (searchTask != null) searchTask.cancel();
        searchTask = new Task<>()
        {
            @Override
            protected Void call()
            {
                if (query == null || query.isEmpty())
                {
                    Platform.runLater(() -> { if (currentFolder != null) loadFolder(currentFolder); });
                    return null;
                }
                List<File> results = new ArrayList<>();
                searchRecursive(currentFolder, query.toLowerCase(), results);
                Platform.runLater(() -> {
                    currentFiles = FXCollections.observableArrayList(results);
                    applyToActiveView();
                    refreshSelectionState();
                });
                return null;
            }
        };
        Thread t = new Thread(searchTask, "Lensora-file-search-task");
        t.setDaemon(true);
        t.start();
    }

    public void updateSearchDebounce()
    {
        int ms = AppSettings.getInstance().getSearchDebounceMs();
        searchDebounce.setDuration(Duration.millis(ms));
    }

    private void searchRecursive(File folder, String query, List<File> results)
    {
        if (folder == null || !folder.isDirectory() || searchTask.isCancelled()) return;
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File f : files)
        {
            if (searchTask.isCancelled()) return;
            if (f.getName().toLowerCase().contains(query)) results.add(f);
            if (f.isDirectory()) searchRecursive(f, query, results);
        }
    }
}