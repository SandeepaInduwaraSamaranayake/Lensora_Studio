package com.lensora.lensorastudio.managers;

import com.lensora.lensorastudio.util.ClipboardFormats;
import com.lensora.lensorastudio.util.ErrorHandler;
import com.lensora.lensorastudio.util.FileIconUtil;
import com.lensora.lensorastudio.util.FileSizeFormatter;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Owns the file listing: the details TableView, ListView, and icon/thumbnail
 * FlowPane — including which one is currently visible — plus recursive
 * search within the current folder.
 */
public class FileListingManager
{
    private final TableView<File> fileTable;
    private final TableColumn<File, String> colFileName, colFileType, colFileSize, colFileDimensions, colFileModified;
    private final Label lblCurrentFolder, lblFileCount;
    private final TextField fileSearchField;
    private final ToggleButton btnDetails, btnList, btnIcons, btnThumbnails;
    private final ListView<File> fileListView;
    private final ScrollPane iconScrollPane;
    private final FlowPane iconFlowPane;
    private final ToggleGroup viewToggleGroup = new ToggleGroup();

    private final Map<File, SimpleStringProperty> dimensionProps = new ConcurrentHashMap<>();
    private final Map<File, String> dimensionCache = new ConcurrentHashMap<>();
    private BiConsumer<List<File>, Boolean> onExternalFilesDropped; // (files, isMove — always false here)
    private final ObjectProperty<File> selectedFileProperty = new SimpleObjectProperty<>();

    private File currentFolder;
    private Task<Void> searchTask;

    public FileListingManager(TableView<File> fileTable,
                            TableColumn<File, String> colFileName, TableColumn<File, String> colFileType,
                            TableColumn<File, String> colFileSize, TableColumn<File, String> colFileDimensions,
                            TableColumn<File, String> colFileModified,
                            Label lblCurrentFolder, Label lblFileCount, TextField fileSearchField,
                            ToggleButton btnDetails, ToggleButton btnList, ToggleButton btnIcons, ToggleButton btnThumbnails,
                            ListView<File> fileListView, ScrollPane iconScrollPane, FlowPane iconFlowPane)
    {
        this.fileTable = fileTable;
        this.colFileName = colFileName;
        this.colFileType = colFileType;
        this.colFileSize = colFileSize;
        this.colFileDimensions = colFileDimensions;
        this.colFileModified = colFileModified;
        this.lblCurrentFolder = lblCurrentFolder;
        this.lblFileCount = lblFileCount;
        this.fileSearchField = fileSearchField;
        this.btnDetails = btnDetails;
        this.btnList = btnList;
        this.btnIcons = btnIcons;
        this.btnThumbnails = btnThumbnails;
        this.fileListView = fileListView;
        this.iconScrollPane = iconScrollPane;
        this.iconFlowPane = iconFlowPane;

        setupSelectionMode();
        setupToggleGroup();
        setupTableColumns();
        setupViewSwitching();
        setupDragAndDrop();
        setupSearch();
        setupListeners();
    }

    public TableView<File> getFileTable() { return fileTable; }
    public File getSelectedFile() { return fileTable.getSelectionModel().getSelectedItem(); }
    public List<File> getSelectedFiles() { return fileTable.getSelectionModel().getSelectedItems(); }

    /** Called by FileManager to wire external drop-into-current-folder behaviour. */
    public void setOnFilesDroppedIntoCurrentFolder(BiConsumer<List<File>, Boolean> callback) { this.onExternalFilesDropped = callback; }
    

    // ─── Listeners ──────────────────────────────────────────────────────────
    public void setupListeners()
    {
        fileTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            selectedFileProperty.set(newVal);
        });
    }

    private void setupSelectionMode() 
    {
        fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    // ─── Loading ────────────────────────────────────────────────────────────

    public void loadFolder(File folder)
    {
        dimensionProps.clear();
        dimensionCache.clear();

        this.currentFolder = folder;
        if (folder == null || !folder.isDirectory())
        {
            fileTable.getItems().clear();
            lblCurrentFolder.setText("");
            lblFileCount.setText("");
            return;
        }

        File[] files = folder.listFiles(File::isFile);
        if (files == null) files = new File[0];
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        fileTable.getItems().setAll(files);
        lblCurrentFolder.setText(folder.getName());
        lblFileCount.setText(files.length + " files");

        refreshActiveView();

        if (!fileSearchField.getText().isEmpty())
        {
            fileSearchField.clear();
        }
    }

    // Reloads the file table
    public void refresh()
    {
        loadFolder(currentFolder);
    }

    /**
     * Sets up the file table columns (name, type, size, modified).
     */
    private void setupTableColumns()
    {
        colFileName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));

        colFileType.setCellValueFactory(c -> {
            String name = c.getValue().getName();
            int idx = name.lastIndexOf('.');
            return new SimpleStringProperty(idx > 0 ? name.substring(idx + 1) : "");
        });

        colFileSize.setCellValueFactory(c -> {
            long size = c.getValue().length();
            return new SimpleStringProperty(size > 0 ? FileSizeFormatter.formatFileSize(size) : "");
        });

        colFileDimensions.setCellValueFactory(c -> {
            File file = c.getValue();
            SimpleStringProperty prop = dimensionProps.get(file);
            if (prop == null)
            {
                prop = new SimpleStringProperty("");
                dimensionProps.put(file, prop);
                loadDimensions(file, prop);
            }
            return prop;
        });

        colFileModified.setCellValueFactory(c -> 
            new SimpleStringProperty(
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(c.getValue().lastModified()), 
                    ZoneId.systemDefault()
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
    }

    private void loadDimensions(File file, SimpleStringProperty prop)
    {
        if (!isImageFile(file) && !isVideoFile(file))
        {
            prop.set("");
            return;
        }
        String cached = dimensionCache.get(file);
        if (cached != null)
        {
            prop.set(cached);
            return;
        }

        if (isImageFile(file)) 
        {
            Image img = new Image(file.toURI().toString(), true);
            img.progressProperty().addListener((obs, old, progress) -> {
                if (progress.doubleValue() >= 1.0)
                {
                    String dims = (int) img.getWidth() + "x" + (int) img.getHeight();
                    dimensionCache.put(file, dims);
                    Platform.runLater(() -> prop.set(dims));
                }
            });
            img.exceptionProperty().addListener((obs, old, ex) -> {
                if (ex != null)
                {
                    dimensionCache.put(file, "");
                    Platform.runLater(() -> prop.set(""));
                }
            });
        }
        else if(isVideoFile(file))
        {
            //todo
        }
    }

    // ─── View switching ─────────────────────────────────────────────────────

    private void setupToggleGroup()
    {
        btnDetails.setToggleGroup(viewToggleGroup);
        btnList.setToggleGroup(viewToggleGroup);
        btnIcons.setToggleGroup(viewToggleGroup);
        btnThumbnails.setToggleGroup(viewToggleGroup);
    }

    private void setupViewSwitching()
    {
        viewToggleGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            if (newVal == btnDetails) switchView("details");
            else if (newVal == btnList) switchView("list");
            else if (newVal == btnIcons) switchView("icons");
            else if (newVal == btnThumbnails) switchView("thumbnails");
        });
        btnDetails.setSelected(true);
        switchView("details");
    }

    private void switchView(String view)
    {
        fileTable.setVisible(false);
        fileTable.setManaged(false);
        fileListView.setVisible(false);
        fileListView.setManaged(false);
        iconScrollPane.setVisible(false);
        iconScrollPane.setManaged(false);

        switch (view)
        {
            case "details" -> { fileTable.setVisible(true); fileTable.setManaged(true);}
            case "list" -> { fileListView.setVisible(true); fileListView.setManaged(true); populateListView(fileTable.getItems()); }
            case "icons", "thumbnails" -> { iconScrollPane.setVisible(true); iconScrollPane.setManaged(true); populateIconView(view.equals("thumbnails")); }
        }
    }

    private void refreshActiveView()
    {
        String view = getCurrentView();
        if (view.equals("list")) populateListView(fileTable.getItems());
        else if (view.equals("icons") || view.equals("thumbnails")) populateIconView(view.equals("thumbnails"));
    }

    private String getCurrentView()
    {
        Toggle selected = viewToggleGroup.getSelectedToggle();
        if (selected == btnDetails) return "details";
        if (selected == btnList) return "list";
        if (selected == btnIcons) return "icons";
        if (selected == btnThumbnails) return "thumbnails";
        return "details";
    }

    private void populateListView(ObservableList<File> files)
    {
        fileListView.setItems(files);
        fileListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(File file, boolean empty)
            {
                super.updateItem(file, empty);
                if (empty || file == null) { setText(null); setGraphic(null); }
                else { setText(file.getName()); setGraphic(FileIconUtil.getFileIcon(file, 20)); }
            }
        });
    }

    private void populateIconView(boolean thumbnails)
    {
        iconFlowPane.getChildren().clear();
        for (File file : fileTable.getItems())
        {
            VBox card = new VBox(5);
            card.setAlignment(Pos.CENTER);
            card.setPrefSize(120, 120);
            card.setStyle("-fx-background-color: -color-bg-muted; -fx-border-color: -color-border-default; -fx-border-radius: 4; -fx-background-radius: 4;");

            Node iconNode;
            if (thumbnails && isImageFile(file))
            {
                ImageView imageView = new ImageView();
                loadThumbnail(file, imageView);
                imageView.setFitWidth(80);
                imageView.setFitHeight(80);
                iconNode = imageView;
            }
            else
            {
                iconNode = FileIconUtil.getFileIcon(file, 64);
            }

            Label nameLabel = new Label(file.getName());
            nameLabel.setWrapText(true);
            nameLabel.setMaxWidth(100);
            nameLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            card.getChildren().addAll(iconNode, nameLabel);

            // drag handler
            card.setOnDragDetected(event -> {
                List<File> selected = getSelectedFiles().isEmpty() ? List.of(file) : getSelectedFiles();
                Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putFiles(selected);
                content.put(ClipboardFormats.INTERNAL_DRAG, true);
                db.setContent(content);
                event.consume();
            });

            // click handler
            card.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) openFile(file);
            });
            iconFlowPane.getChildren().add(card);
        }
    }

    private void loadThumbnail(File file, ImageView target)
    {
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() { return new Image(file.toURI().toString(), 80, 80, true, true); }
        };
        task.setOnSucceeded(e -> target.setImage(task.getValue()));
        new Thread(task).start();
    }

    private void openFile(File file)
    {
        try { Desktop.getDesktop().open(file); }
        catch (IOException ex) { ErrorHandler.show(null, "Could not open file", ex); }
    }

    private boolean isImageFile(File file)
    {
        String ext = getFileExtension(file);
        return ext != null && (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")
                || ext.equals("gif") || ext.equals("bmp") || ext.equals("tiff"));
    }

    private boolean isVideoFile(File file)
    {
        String ext = getFileExtension(file);
        return ext != null && (ext.equals("mp4") || ext.equals("mov") || ext.equals("avi") || ext.equals("mkv") || ext.equals("wmv") || ext.equals("flv") || ext.equals("webm"));
    }

    private String getFileExtension(File file)
    {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1).toLowerCase() : null;
    }

    // ─── Search ─────────────────────────────────────────────────────────────

    private void setupSearch()
    {
        fileSearchField.textProperty().addListener((obs, old, newVal) -> {
            if (searchTask != null) searchTask.cancel();
            searchTask = new Task<>() {
                @Override
                protected Void call() {
                    if (newVal == null || newVal.isEmpty())
                    {
                        Platform.runLater(() -> { if (currentFolder != null) loadFolder(currentFolder); });
                        return null;
                    }
                    List<File> results = new ArrayList<>();
                    searchRecursive(currentFolder, newVal.toLowerCase(), results);
                    Platform.runLater(() -> displaySearchResults(results));
                    return null;
                }
            };
            new Thread(searchTask).start();
        });
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

    private void displaySearchResults(List<File> results)
    {
        fileTable.getItems().setAll(results);
        refreshActiveView();
    }

    public boolean isFocused()
    {
        return fileTable.isFocused();
    }

    public ObjectProperty<File> selectedFileProperty() 
    {
        return selectedFileProperty;
    }

    // ─── Drag&Drop ──────────────────────────────────────────────────────────

    /** Sets up drag source + drop target behaviour for all three views. */
    private void setupDragAndDrop()
    {
        setupDragSource(fileTable);
        setupDragSource(fileListView);
        // Icon view cards are built dynamically in populateIconView(); handled there.

        setupDropTarget(fileTable);
        setupDropTarget(fileListView);
        setupDropTarget(iconScrollPane);
    }

    private void setupDragSource(Node source)
    {
        source.setOnDragDetected(event -> {
            List<File> selected = getSelectedFiles();
            if (selected.isEmpty()) return;

            Dragboard db = source.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putFiles(selected);
            content.put(ClipboardFormats.INTERNAL_DRAG, true);
            db.setContent(content);
            event.consume();
        });
    }

    private void setupDropTarget(javafx.scene.Node target)
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

                // Files dragged from elsewhere in this app onto the file
                // listing itself (not a folder) is a no-op - they're
                // already in the current folder. Only handle genuine
                // external OS drag-ins here.
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

}