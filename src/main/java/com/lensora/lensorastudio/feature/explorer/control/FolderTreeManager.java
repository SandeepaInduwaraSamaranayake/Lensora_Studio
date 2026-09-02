package com.lensora.lensorastudio.feature.explorer.control;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import com.lensora.lensorastudio.core.context.ProjectContext;
import com.lensora.lensorastudio.core.io.InstrumentedFileIO;

/**
 * Folder-tree subsystem. Delegates to:
 *  - FolderTreeViewManager    (TreeView rendering, cell drag&drop, tree structure)
 *  - FolderNavigationManager  (back/forward history, breadcrumb, current folder)
 *  - FolderContextMenuManager (new folder / copy / paste / delete / open / copy path)
 */
public class FolderTreeManager
{
    private final FolderTreeViewManager treeViewManager;
    private final FolderNavigationManager navigationManager;
    private final FolderContextMenuManager contextMenuManager;
    private final InstrumentedFileIO fileIO;
    private final ProjectContext projectContext;
    
    private Runnable onRefreshRequested;

    public FolderTreeManager(   TreeView<File> folderTree,
                                HBox breadcrumbContainer,
                                Button btnBack, 
                                Button btnForward,
                                Label lblFolderHeader,
                                ProjectContext projectContext,
                                InstrumentedFileIO fileIO)
    {
        this.projectContext = projectContext;
        this.fileIO = fileIO;

        this.treeViewManager = new FolderTreeViewManager(folderTree);
        this.navigationManager = new FolderNavigationManager(breadcrumbContainer, btnBack, btnForward, lblFolderHeader, treeViewManager);
        this.contextMenuManager = new FolderContextMenuManager(folderTree, treeViewManager, navigationManager, fileIO);

        // Tree selection -> navigation, with the same re-entry guard the
        // original single-class implementation had (don't navigate again
        // if the selected folder is already the current one).
        treeViewManager.setOnTreeSelectionChanged(folder -> {
            if (!folder.equals(navigationManager.getCurrentFolder()))
            {
                navigationManager.navigateTo(folder);
            }
        });
    }

    /** Small local functional interface - java.util has no 3-arg Consumer. */
    @FunctionalInterface
    public interface TriConsumer<A, B, C>
    {
        void accept(A a, B b, C c);
    }

    public void setOnFolderSelected(Consumer<File> callback) { navigationManager.setOnFolderSelected(callback); }
    public void setOnPathChanged(Consumer<String> callback) { navigationManager.setOnPathChanged(callback); }
    public void setOnRefreshRequested(Runnable callback) { this.onRefreshRequested = callback; }
    public void setOnFilesDropped(TriConsumer<List<File>, File, Boolean> callback) { treeViewManager.setOnFilesDropped(callback); }
    public void setOnPasteRequested(Runnable callback) { contextMenuManager.setOnPasteRequested(callback); }
    public void setOnNavigationPersisted(Consumer<File> callback) { navigationManager.setOnNavigationPersisted(callback); }
    public void setStage(Stage stage) { contextMenuManager.setOwnerStage(stage); }

    public File getCurrentFolder() { return navigationManager.getCurrentFolder(); }
    public File getProjectRoot() { return projectContext.getProjectRoot(); }


    // ─── Loading ────────────────────────────────────────────────────────────

    public void loadProjectPath(String path)
    {
        File root = treeViewManager.loadProjectPath(path);
        if (root == null) return;

        // Update the single source of truth context
        projectContext.setProjectRoot(root);

        navigationManager.resetHistory();
        navigationManager.navigateTo(root);
    }

    public void refreshSelected()
    {
        treeViewManager.refreshSelected(onRefreshRequested);
    }

    public void refreshFolder(File folder)
    {
        treeViewManager.refreshFolder(folder);
    }

    // ─── Navigation ─────────────────────────────────────────────────────────

    public void navigateTo(File folder) { navigationManager.navigateTo(folder); }
    public void goBack() { navigationManager.goBack(); }
    public void goForward() { navigationManager.goForward(); }

    // expose passthroughs
    public File getSelectedFolder() { return treeViewManager.getSelectedFolder(); }
    public void copySelectedFolder() { contextMenuManager.copySelectedFolder(); }
    public void deleteSelectedFolder() { contextMenuManager.deleteSelectedFolder(); }
    public void createNewFolder()      { contextMenuManager.createNewFolder(); }
    public void renameSelectedFolder()  { contextMenuManager.renameFolder(); }

    /**
     * Expands every ancestor of the given relative path (relative to the
     * current projectRoot) and selects the final folder.
     */
    public void expandAndSelectRelativePath(String relativePath)
    {
        navigationManager.expandAndSelectRelativePath(relativePath);
    }

    /** Returns the current folder's path relative to projectRoot, or null if not under it. */
    public String getCurrentFolderRelativePath()
    {
        return navigationManager.getCurrentFolderRelativePath();
    }

    public boolean isFocused()
    {
        return treeViewManager.isFocused();
    }
}