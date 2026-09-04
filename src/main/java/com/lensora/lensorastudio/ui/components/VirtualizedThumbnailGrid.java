package com.lensora.lensorastudio.ui.components;

import com.lensora.lensorastudio.media.service.ImageValidator;
import com.lensora.lensorastudio.media.service.ThumbnailService;
import com.lensora.lensorastudio.ui.util.FileIconUtil;
import com.lensora.lensorastudio.util.ClipboardFormats;

import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A virtualized grid of thumbnail cards, built on top of a {@link ListView}.
 * 
 * <p><b>Key features:</b></p>
 * <ul>
 *   <li>Virtualization - only visible rows are rendered, enabling smooth scrolling with thousands of files.</li>
 *   <li>Multi‑selection with Ctrl/Shift (click, range, toggle) and keyboard (arrows, Shift+arrows, Ctrl+arrows).</li>
 *   <li>Zoom support - press <kbd>Ctrl++</kbd> (or <kbd>Ctrl+=</kbd>) to enlarge, <kbd>Ctrl+-</kbd> to shrink.</li>
 *   <li>Keyboard shortcuts: <kbd>Ctrl+A</kbd> selects all, <kbd>Escape</kbd> clears selection.</li>
 *   <li>Right‑click preserves the current selection (adds the clicked file only if it was not already selected).</li>
 *   <li>Drag‑and‑drop: dragging from a card moves all currently selected files.</li>
 *   <li>Clicking on empty space clears the selection.</li>
 *   <li>Dynamic spacing - thumbnails automatically spread out to fill the row width.</li>
 * </ul>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * VirtualizedThumbnailGrid grid = new VirtualizedThumbnailGrid();
 * grid.setFiles(fileList);
 * grid.attachSharedContextMenu(myContextMenu);
 * grid.setOnDoubleClick(file -> openFile(file));
 * someParent.getChildren().add(grid.getNode());
 * }</pre>
 * 
 * <p><b>Thread‑safety:</b> All operations must be performed on the JavaFX Application Thread.</p>
 */
public class VirtualizedThumbnailGrid
{
    // ─── Zoom constants ────────────────────────────────────────────────────

     /** The base size of a thumbnail card (pixels). This is multiplied by the zoom factor. */
    private static final double BASE_CARD_SIZE  = 120;
    
    /** The minimum zoom factor (50% of base size). */
    private static final double MIN_ZOOM        = 0.5;

    /** The maximum zoom factor (200% of base size). */
    private static final double MAX_ZOOM        = 2.0;

    /** The step size for each zoom adjustment. */
    private static final double ZOOM_STEP       = 0.1;

    /** The ratio of the icon size (image or file icon) to the card size. */
    private static final double ICON_RATIO      = 0.67;

    // ─── Layout constants ──────────────────────────────────────────────────

    /** The minimum gap between cards in a row (pixels). When dynamic spacing is applied, this value is the base. */
    private static final double CARD_GAP = 10;

    /** The pseudo‑class used to mark a card as selected. Applied via CSS. */
    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");

    // ─── Fields ────────────────────────────────────────────────────────────

    /** The underlying ListView that provides virtualization and row‑based layout. */
    private final ListView<List<File>> rowListView = new ListView<>();

    /** The complete list of files currently displayed in the grid. */
    private List<File> allFiles = List.of();

    /** Whether thumbnails (vs. generic file icons) should be shown for image files. */
    private boolean thumbnailsEnabled = true;

    /** The current zoom factor (1.0 = 100%). */
    private double zoomFactor = 1.0;

    /** The actual card size in pixels, computed as {@code BASE_CARD_SIZE * zoomFactor}. */
    private double currentCardSize = BASE_CARD_SIZE;

    // ─── Selection state ──────────────────────────────────────────────────

    /** The set of currently selected files, maintaining insertion order. */
    private final Set<File> selectedFiles = new LinkedHashSet<>();

    /** The anchor file for Shift‑range selection. Initially null, set to the first file in the selection. */
    private File selectionAnchor;

    /** The last file that was selected (used for arrow‑key navigation and as the starting point for Shift‑range). */
    private File lastSelectedFile;

    // ─── Callbacks ─────────────────────────────────────────────────────────

    /** Called when a file is double‑clicked. The consumer receives the clicked file. */
    private Consumer<File> onDoubleClick;

    /** The shared context menu attached to each card. Set via {@link #attachSharedContextMenu(ContextMenu)}. */
    private ContextMenu sharedMenu;

    /** Called whenever the selection changes. The consumer receives the first selected file (or null if none). */
    private Consumer<File> onSelectionChanged;

    // ─── Layout State ─────────────────────────────────────────────────────

    /** The number of columns (cards per row) from the last regrouping. Used to avoid unnecessary recalculations. */
    private int lastCalculatedColumns = -1;

    /** The current number of columns (cards per row), determined by available width and card size. */
    private int currentColumns = 0;


    // ─── Constructor ──────────────────────────────────────────────────────

    /**
     * Constructs a new virtualized thumbnail grid. The grid is initially empty.
     * 
     * <p>It sets up the ListView, disables the default selection model, registers
     * a width listener for dynamic regrouping, handles keyboard events, and
     * clears selection when clicking on empty space.</p>
     */
    public VirtualizedThumbnailGrid()
    {
        rowListView.setCellFactory(lv -> new RowCell());
        rowListView.getStyleClass().add("thumbnail-grid");
        rowListView.setFocusTraversable(true);
        rowListView.setSelectionModel(null); // manual selection

        // Regroup rows when the ListView's width changes significantly.
        rowListView.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && Math.abs(newVal.doubleValue() - oldVal.doubleValue()) > 2.0) 
            {
                regroupIntoRows();
            }
        });

        // Intercept key events for shortcuts (Ctrl+A, Escape, zoom, arrows).
        rowListView.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);

        // Clear selection when clicking on empty space (not on a card).
        rowListView.setOnMouseClicked(event -> {
            Node target = event.getPickResult().getIntersectedNode();
            boolean isCard = false;
            Node current = target;
            while (current != null) {
                if (current instanceof VBox && current.getStyleClass().contains("thumbnail-card")) {
                    isCard = true;
                    break;
                }
                current = current.getParent();
            }
            if (!isCard && !event.isControlDown() && !event.isShiftDown()) {
                clearSelection();
                rowListView.requestFocus();
            }
        });
        }

    // ─── Public API ─────────────────────────────────────────────────────────

    /**
     * Returns the JavaFX node that represents the grid. This is the {@link ListView} itself.
     * 
     * @return the root node of the grid
     */
    public Node getNode() { return rowListView; }

    /**
     * Enables or disables thumbnail generation for image files.
     * 
     * @param enabled if {@code true}, thumbnails will be shown for supported images;
     *                otherwise, a generic file icon is used.
     */
    public void setThumbnailsEnabled(boolean enabled) { this.thumbnailsEnabled = enabled; }

    /**
     * Sets the double‑click handler for each card.
     * 
     * @param handler a consumer that receives the double‑clicked file; may be {@code null}
     */
    public void setOnDoubleClick(Consumer<File> handler) { this.onDoubleClick = handler; }

    /**
     * Attaches a shared context menu to every card. The same menu instance is used for all cards.
     * 
     * @param menu the context menu to show; may be {@code null} to remove the menu
     */
    public void attachSharedContextMenu(ContextMenu menu) { this.sharedMenu = menu; }

    /**
     * Sets a listener that is notified whenever the selection changes.
     * 
     * @param handler a consumer that receives the first selected file (or {@code null} if none)
     */
    public void setOnSelectionChanged(Consumer<File> handler) { this.onSelectionChanged = handler; }

    /**
     * Returns a copy of the currently selected files.
     * 
     * @return a new {@code List<File>} containing the selected files
     */
    public List<File> getSelectedFiles() { return new ArrayList<>(selectedFiles); }

    /**
     * Returns whether the grid currently has keyboard focus.
     * 
     * @return {@code true} if the grid is focused
     */
    public boolean isFocused() { return rowListView.isFocused(); }

    /**
     * Replaces the entire content of the grid with a new list of files.
     * The selection is cleared and the grid is re‑grouped.
     * 
     * @param files the new list of files; may be {@code null}, treated as an empty list
     */
    public void setFiles(List<File> files) 
    {
        this.allFiles = (files != null) ? files : List.of();
        this.lastCalculatedColumns = -1;
        clearSelection();
        regroupIntoRows();
    }

    /**
     * Clears the grid, removing all files and resetting state.
     */
    public void clear() 
    {
        allFiles = List.of();
        clearSelection();
        lastCalculatedColumns = -1;
        currentColumns = 0;
        rowListView.setItems(FXCollections.observableArrayList());
    }

    // ─── Selection management ─────────────────────────────────────────────

    /**
     * Clears the current selection and updates the UI.
     */
    private void clearSelection() 
    {
        selectedFiles.clear();
        selectionAnchor = null;
        lastSelectedFile = null;
        updateAllCards();
        if (onSelectionChanged != null) onSelectionChanged.accept(null);
    }

    /**
     * Core selection logic. Supports single selection, toggle (Ctrl), range (Shift),
     * and toggle‑range (Ctrl+Shift).
     * 
     * @param file  the file to select or deselect
     * @param shift whether the Shift key is held (range selection)
     * @param ctrl  whether the Ctrl/Cmd key is held (toggle)
     */
    private void selectFile(File file, boolean shift, boolean ctrl) 
    {
        if (file == null || !allFiles.contains(file)) return;

        if (shift && selectionAnchor != null) 
        {
            // Range selection
            List<File> range = getFileRange(selectionAnchor, file);
            if (ctrl) 
            {
                // Ctrl+Shift: toggle the entire range
                for (File f : range) 
                {
                    if (selectedFiles.contains(f)) selectedFiles.remove(f);
                    else selectedFiles.add(f);
                }
            } 
            else 
            {
                // Shift only: select the range, clear others
                selectedFiles.clear();
                selectedFiles.addAll(range);
            }
        } 
        else if (ctrl) 
        {
            // Ctrl+click: toggle this single file
            if (selectedFiles.contains(file)) selectedFiles.remove(file);
            else selectedFiles.add(file);
        } 
        else 
        {
            // Single click (no modifiers): select only this file
            selectedFiles.clear();
            selectedFiles.add(file);
        }

         // Update the selection anchor and last selected file.
        if (!selectedFiles.isEmpty()) 
        {
            selectionAnchor = selectedFiles.iterator().next();
            lastSelectedFile = file;
        } 
        else 
        {
            selectionAnchor = null;
            lastSelectedFile = null;
        }

        updateAllCards();
        if (onSelectionChanged != null) 
        {
            File first = selectedFiles.isEmpty() ? null : selectedFiles.iterator().next();
            onSelectionChanged.accept(first);
        }
    }

    /**
     * Computes the range of files between two anchor files in the full list.
     * 
     * @param anchor the start of the range
     * @param target the end of the range
     * @return a list containing all files from the start index to the end index (inclusive)
     */
    private List<File> getFileRange(File anchor, File target) 
    {
        int anchorIdx = allFiles.indexOf(anchor);
        int targetIdx = allFiles.indexOf(target);
        if (anchorIdx < 0 || targetIdx < 0) return List.of();
        int start = Math.min(anchorIdx, targetIdx);
        int end = Math.max(anchorIdx, targetIdx);
        List<File> range = new ArrayList<>();
        for (int i = start; i <= end; i++) 
        {
            range.add(allFiles.get(i));
        }
        return range;
    }

    /**
     * Updates the visual selection state of all currently visible cards by
     * applying or removing the {@code :selected} pseudo‑class.
     */
    private void updateAllCards() 
    {
        for (Node node : rowListView.lookupAll(".list-cell")) 
        {
            if (node instanceof RowCell cell) 
            {
                cell.updateSelectionStates(selectedFiles);
            }
        }
    }

    // ─── Zoom ──────────────────────────────────────────────────────────────

    /**
     * Adjusts the zoom by a delta, clamping to the allowed range.
     * Triggers a re‑group of rows to accommodate the new card size.
     * 
     * @param delta the positive or negative amount to add to the current zoom factor
     */
    private void adjustZoom(double delta) 
    {
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomFactor + delta));
        if (newZoom == zoomFactor) return;
        zoomFactor = newZoom;
        currentCardSize = BASE_CARD_SIZE * zoomFactor;
        lastCalculatedColumns = -1;
        regroupIntoRows();
    }

    // ─── Navigation ────────────────────────────────────────────────────────

    /**
     * Selects a single file (clearing any previous selection) and scrolls it into view.
     * 
     * @param file the file to select and scroll to
     */
    private void selectAndScroll(File file) 
    {
        selectFile(file, false, false);
        scrollToFile(file);
    }

    /**
     * Scrolls the ListView so that the row containing the given file is visible,
     * but only if it is not already visible.
     * 
     * @param file the file to bring into view
     */
    private void scrollToFile(File file) 
    {
        if (file == null || allFiles.isEmpty() || currentColumns <= 0) return;
        int index = allFiles.indexOf(file);
        if (index < 0) return;
        int rowIndex = index / currentColumns;
        if (!isRowVisible(rowIndex)) {
            rowListView.scrollTo(rowIndex);
        }
    }

    /**
     * Checks whether a given row index is currently visible in the viewport.
     * 
     * @param rowIndex the row index to check
     * @return {@code true} if at least part of the row is visible
     */
    private boolean isRowVisible(int rowIndex) 
    {
        for (Node node : rowListView.lookupAll(".list-cell")) 
        {
            if (node instanceof ListCell<?> cell && cell.getIndex() == rowIndex && cell.isVisible()) 
            {
                return true;
            }
        }
        return false;
    }

    // ─── Regrouping ────────────────────────────────────────────────────────

    /**
     * Rebuilds the rows of the grid based on the current available width and card size.
     * The grid is re‑grouped into rows of cards, and the selection is restored
     * (if any files are selected) and scrolled into view.
     */
    private void regroupIntoRows() 
    {
        double availableWidth = rowListView.getWidth() - 10;
        if (availableWidth <= 0 || allFiles.isEmpty()) 
        {
            rowListView.setItems(FXCollections.observableArrayList());
            return;
        }

        int perRow = Math.max(1, (int) (availableWidth / (currentCardSize + CARD_GAP)));
        currentColumns = perRow;

        if (perRow == lastCalculatedColumns) 
        {
            return;
        }
        lastCalculatedColumns = perRow;

        List<List<File>> rows = new ArrayList<>();
        for (int i = 0; i < allFiles.size(); i += perRow) 
        {
            rows.add(allFiles.subList(i, Math.min(i + perRow, allFiles.size())));
        }

        rowListView.setItems(FXCollections.observableArrayList(rows));

        if (!selectedFiles.isEmpty()) {
            updateAllCards();
            scrollToFile(selectedFiles.iterator().next());
        }
    }

    // ─── Keyboard shortcuts ───────────────────────────────────────────────

    /**
     * Handles key presses for shortcuts:
     * <ul>
     *   <li><kbd>Ctrl+A</kbd> – select all files</li>
     *   <li><kbd>Escape</kbd> – clear selection</li>
     *   <li><kbd>Ctrl++</kbd>, <kbd>Ctrl+=</kbd>, <kbd>Ctrl+-</kbd> – zoom</li>
     *   <li><kbd>Arrow keys</kbd> – move selection (Shift extends, Ctrl toggles)</li>
     * </ul>
     * All other key events are allowed to bubble up to the parent scene.
     * 
     * @param event the key event
     */
    private void handleKeyPressed(KeyEvent event) 
    {
        // ── Ctrl+A: Select All ──────────────────────────────────────────
        if (event.isControlDown() && event.getCode() == KeyCode.A) 
        {
            if (!allFiles.isEmpty() && rowListView.isFocused()) 
            {
                selectedFiles.clear();
                selectedFiles.addAll(allFiles);
                selectionAnchor = allFiles.get(0);
                lastSelectedFile = allFiles.get(allFiles.size() - 1);
                updateAllCards();
                if (onSelectionChanged != null) onSelectionChanged.accept(selectionAnchor);
                event.consume();
                return;
            }
        }

        // ── Escape: Clear Selection ─────────────────────────────────────
        if (event.getCode() == KeyCode.ESCAPE) 
        {
            if (!selectedFiles.isEmpty() && rowListView.isFocused()) 
            {
                clearSelection();
                event.consume();
                return;
            }
        }

        // ── Zoom (Ctrl++ / Ctrl+-) ─────────────────────────────────────
        if (event.isControlDown()) 
        {
            KeyCode code = event.getCode();
            if (code == KeyCode.PLUS || code == KeyCode.EQUALS || code == KeyCode.ADD) 
            {
                adjustZoom(ZOOM_STEP);
                event.consume();
                return;
            }
            if (code == KeyCode.MINUS || code == KeyCode.SUBTRACT) 
            {
                adjustZoom(-ZOOM_STEP);
                event.consume();
                return;
            }
        }

        // ── Arrow keys ──────────────────────────────────────────────────
        KeyCode code = event.getCode();
        if (code != KeyCode.LEFT && code != KeyCode.RIGHT && code != KeyCode.UP && code != KeyCode.DOWN) 
        {
            return; // Let other shortcuts bubble up (Ctrl+C, Ctrl+V, Delete)
        }

        if (!rowListView.isFocused()) return;
        if (allFiles.isEmpty() || currentColumns <= 0) return;

        File currentFile = lastSelectedFile;
        if (currentFile == null || !allFiles.contains(currentFile)) {
            selectAndScroll(allFiles.get(0));
            event.consume();
            return;
        }

        int currentIndex = allFiles.indexOf(currentFile);
        int newIndex = -1;
        int col = currentIndex % currentColumns;
        int row = currentIndex / currentColumns;

        switch (code) 
        {
            case LEFT:
                if (col > 0) newIndex = currentIndex - 1;
                else {
                    int prevRowStart = currentIndex - currentColumns;
                    if (prevRowStart >= 0) 
                    {
                        int prevRowEnd = Math.min(prevRowStart + currentColumns - 1, allFiles.size() - 1);
                        newIndex = prevRowEnd;
                    }
                }
                break;
            case RIGHT:
                int rowEnd = Math.min((row + 1) * currentColumns - 1, allFiles.size() - 1);
                if (currentIndex < rowEnd) newIndex = currentIndex + 1;
                else 
                {
                    int nextRowStart = (row + 1) * currentColumns;
                    if (nextRowStart < allFiles.size()) newIndex = nextRowStart;
                }
                break;
            case UP:
                if (currentIndex - currentColumns >= 0) newIndex = currentIndex - currentColumns;
                break;
            case DOWN:
                if (currentIndex + currentColumns < allFiles.size()) newIndex = currentIndex + currentColumns;
                break;
            default: return;
        }

        if (newIndex >= 0 && newIndex < allFiles.size()) 
        {
            File newFile = allFiles.get(newIndex);
            boolean shift = event.isShiftDown();
            boolean ctrl = event.isControlDown();

            // Pass modifiers to support Shift‑range and Ctrl‑toggle with arrows.
            selectFile(newFile, shift, ctrl);
            scrollToFile(newFile);
        }
        event.consume();
    }

    // ─── Row Cell ──────────────────────────────────────────────────────────

    /**
     * A single row cell in the ListView. Each cell holds a horizontal row of thumbnail cards.
     * The cell is responsible for rendering the cards for a given row of files.
     */
    private class RowCell extends ListCell<List<File>> 
    {
        /** The container that holds all cards in this row. */
        private final HBox rowBox = new HBox(CARD_GAP);

        /** A list of cancellation handlers for pending thumbnail loading tasks. */
        private final List<Runnable> activeCancellations = new ArrayList<>();

        /** Maps each file in this row to its corresponding card VBox. */
        private final Map<File, VBox> cardsByFile = new HashMap<>();

        /**
         * Constructs a new RowCell. The cell uses an HBox with the default gap.
         */
        RowCell()
        {
            rowBox.setAlignment(Pos.CENTER_LEFT);
            setGraphic(rowBox);
        }

        /**
         * Updates the cell to display a new row of files. Called by the ListView during virtualization.
         *
         * @param rowFiles the list of files for this row
         * @param empty    whether this cell is empty
         */
        @Override
        protected void updateItem(List<File> rowFiles, boolean empty) 
        {
            super.updateItem(rowFiles, empty);
            cancelPending();
            rowBox.getChildren().clear();
            cardsByFile.clear();

            if (empty || rowFiles == null) 
            {
                setGraphic(null);
                return;
            }

            for (File file : rowFiles) 
            {
                VBox card = buildCard(file);
                cardsByFile.put(file, card);
                rowBox.getChildren().add(card);
            }

            updateSelectionStates(selectedFiles);
            setGraphic(rowBox);
        }

        /**
         * Updates the selection state of all cards in this row based on the current selection set.
         *
         * @param selectedSet the set of currently selected files
         */
        public void updateSelectionStates(Set<File> selectedSet) 
        {
            for (Map.Entry<File, VBox> entry : cardsByFile.entrySet()) 
            {
                boolean isSelected = selectedSet.contains(entry.getKey());
                entry.getValue().pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, isSelected);
            }
        }

        /** Cancels any pending thumbnail loading tasks. */
        private void cancelPending() 
        {
            for (Runnable cancel : activeCancellations) 
            {
                cancel.run();
            }
            activeCancellations.clear();
        }

        /**
         * Builds a single thumbnail card for a given file.
         *
         * @param file the file to display
         * @return a VBox containing the icon (thumbnail or file icon) and the file name
         */
        private VBox buildCard(File file) 
        {
            VBox card = new VBox(5);
            card.setAlignment(Pos.CENTER);
            card.setPrefSize(currentCardSize, currentCardSize);
            card.getStyleClass().add("thumbnail-card");

            // ── Icon (thumbnail or file icon) ──────────────────────────────
            Node iconNode;
            if (thumbnailsEnabled && ImageValidator.isJavaFXLoadable(file)) 
            {
                ImageView imageView = new ImageView();
                double iconSize = currentCardSize * ICON_RATIO;
                imageView.setFitWidth(iconSize);
                imageView.setFitHeight(iconSize);
                imageView.setPreserveRatio(true);
                iconNode = imageView;

                var cancelHandle = new Object() { volatile boolean cancelled = false; };
                ThumbnailService.getInstance().requestThumbnail(file, image -> {
                    if (cancelHandle.cancelled) return;
                    imageView.setImage(image);
                });
                activeCancellations.add(() -> cancelHandle.cancelled = true);
            }
            else
            {
                iconNode = FileIconUtil.getFileIcon(file, ".icon-size-60");
            }

             // ── File name label ─────────────────────────────────────────────
            Label nameLabel = new Label(file.getName());
            nameLabel.setWrapText(true);
            nameLabel.setMaxWidth(currentCardSize - 10);
            nameLabel.setTextAlignment(TextAlignment.CENTER);

            card.getChildren().addAll(iconNode, nameLabel);

            // ── Mouse click ──────────────────────────────────────────────
            card.setOnMouseClicked(event -> {
                boolean shift = event.isShiftDown();
                boolean ctrl = event.isControlDown() || event.isMetaDown();
                selectFile(file, shift, ctrl);
                rowListView.requestFocus();

                if (event.getClickCount() == 2 && onDoubleClick != null) 
                {
                    onDoubleClick.accept(file);
                }
                event.consume();
            });

            // ── Right-click / Context Menu ─────────────────────────────
            // When right‑clicking, if the file is not selected, we add it to the selection
            // without clearing the rest (like a Ctrl+click). This preserves multi‑selection.
            if (sharedMenu != null) {
                card.setOnContextMenuRequested(event -> {
                    // If the file is not selected, add it to the selection (like Ctrl+click)
                    // but do NOT clear the existing selection.
                    if (!selectedFiles.contains(file)) 
                    {
                        selectedFiles.add(file);
                        // Update anchor and last selected
                        selectionAnchor = file;
                        lastSelectedFile = file;
                        updateAllCards();
                        if (onSelectionChanged != null) onSelectionChanged.accept(file);
                    }
                    // Show the context menu
                    sharedMenu.show(card, event.getScreenX(), event.getScreenY());
                    event.consume();
                });
            }

            // ── Drag detection ────────────────────────────────────────────
            card.setOnDragDetected(event -> {
                // Select only this file before dragging (clears any other selection)
                selectFile(file, false, false);
                Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                List<File> filesToDrag = selectedFiles.isEmpty() 
                        ? List.of(file) 
                        : new ArrayList<>(selectedFiles);
                content.putFiles(filesToDrag);
                content.put(ClipboardFormats.INTERNAL_DRAG, true);
                db.setContent(content);
                event.consume();
            });

            return card;
        }
    }
}