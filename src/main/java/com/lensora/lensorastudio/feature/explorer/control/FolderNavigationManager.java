package com.lensora.lensorastudio.feature.explorer.control;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

/**
 * Owns navigation state for the folder explorer: back/forward history,
 * the breadcrumb bar, and the current folder - independent of how the
 * tree itself is rendered (delegates tree selection/expansion to
 * FolderTreeViewManager).
 */
public class FolderNavigationManager
{
    private final HBox breadcrumbContainer;
    private final Button btnBack, btnForward;
    private final Label lblFolderHeader;
    private final FolderTreeViewManager treeViewManager;

    private final Stack<File> backStack = new Stack<>();
    private final Stack<File> forwardStack = new Stack<>();
    private File currentFolder;
    private boolean isNavigatingHistory = false;

    private Consumer<File> onFolderSelected;
    private Consumer<String> onPathChanged;
    private Consumer<File> onNavigationPersisted;

    public FolderNavigationManager(HBox breadcrumbContainer, Button btnBack, Button btnForward,
                                    Label lblFolderHeader, FolderTreeViewManager treeViewManager)
    {
        this.breadcrumbContainer = breadcrumbContainer;
        this.btnBack = btnBack;
        this.btnForward = btnForward;
        this.lblFolderHeader = lblFolderHeader;
        this.treeViewManager = treeViewManager;

        setupNavigationButtons();
    }

    public void setOnFolderSelected(Consumer<File> callback) { this.onFolderSelected = callback; }
    public void setOnPathChanged(Consumer<String> callback) { this.onPathChanged = callback; }
    public void setOnNavigationPersisted(Consumer<File> callback) { this.onNavigationPersisted = callback; }

    public File getCurrentFolder() { return currentFolder; }

    /** Clears back/forward history - call when a new project is loaded. */
    public void resetHistory()
    {
        backStack.clear();
        forwardStack.clear();
    }

    // ─── Navigation ─────────────────────────────────────────────────────────

    public void navigateTo(File folder)
    {
        if (folder == null || !folder.exists()) return;
        if (!isNavigatingHistory && currentFolder != null && !currentFolder.equals(folder))
        {
            backStack.push(currentFolder);
            forwardStack.clear();
        }
        currentFolder = folder;
        updateBreadcrumb(folder);
        updateButtonStates();
        //lblFolderHeader.setText("Folders  [" + folder.getAbsolutePath() + "]");

        // visit folder
        treeViewManager.selectFolderInTree(folder);

        if (onFolderSelected != null)
        {
            onFolderSelected.accept(folder);
        }

        if (onPathChanged != null)
        {
            onPathChanged.accept(folder.getAbsolutePath());
        }

        if (onNavigationPersisted != null)
        {
            String relative = getCurrentFolderRelativePath();
            if (relative != null && !relative.isEmpty()) // only save if non-empty
            {
                onNavigationPersisted.accept(folder);
            }
        }
    }

    public void goBack()
    {
        if (backStack.isEmpty()) return;

        isNavigatingHistory = true;
        try
        {
            forwardStack.push(currentFolder);
            File target = backStack.pop();
            navigateTo(target);
        }
        finally
        {
            isNavigatingHistory = false;
        }
    }

    public void goForward()
    {
        if (forwardStack.isEmpty()) return;

        isNavigatingHistory = true;
        try
        {
            backStack.push(currentFolder);
            File target = forwardStack.pop();
            navigateTo(target);
        }
        finally
        {
            isNavigatingHistory = false;
        }
    }

    private void setupNavigationButtons()
    {
        btnBack.setOnAction(e -> goBack());
        btnForward.setOnAction(e -> goForward());
        updateButtonStates();
    }

    private void updateButtonStates()
    {
        btnBack.setDisable(backStack.isEmpty());
        btnForward.setDisable(forwardStack.isEmpty());
    }

    // ─── Breadcrumb ─────────────────────────────────────────────────────────

    private void updateBreadcrumb(File folder)
    {
        breadcrumbContainer.getChildren().clear();
        if (folder == null) return;

        File projectRoot = treeViewManager.getProjectRoot();

        if (projectRoot == null)
        {
            Button btn = new Button(folder.getAbsolutePath());
            btn.setStyle("-fx-background-color: transparent;");
            breadcrumbContainer.getChildren().add(btn);
            return;
        }

        List<File> segmentFiles = new ArrayList<>();
        List<String> segmentNames = new ArrayList<>();
        segmentFiles.add(projectRoot);
        segmentNames.add(projectRoot.getName());

        if (!folder.equals(projectRoot))
        {
            Path relative = projectRoot.toPath().relativize(folder.toPath());
            for (int i = 0; i < relative.getNameCount(); i++)
            {
                Path fullPath = projectRoot.toPath().resolve(relative.subpath(0, i + 1));
                segmentFiles.add(fullPath.toFile());
                segmentNames.add(relative.getName(i).toString());
            }
        }

        for (int i = 0; i < segmentFiles.size(); i++)
        {
            if (i > 0)
            {
                Label sep = new Label(">");
                breadcrumbContainer.getChildren().add(sep);
            }
            Button btn = new Button(segmentNames.get(i));
            btn.setStyle("-fx-background-color: transparent;");
            final File target = segmentFiles.get(i);
            btn.setOnAction(e -> navigateTo(target));
            breadcrumbContainer.getChildren().add(btn);
        }
    }

    /**
     * Expands every ancestor of the given relative path (relative to the
     * current projectRoot) and selects the final folder. If any segment
     * along the way no longer exists on disk, stops at the deepest folder
     * that still exists.
     */
    public void expandAndSelectRelativePath(String relativePath)
    {
        File projectRoot = treeViewManager.getProjectRoot();
        if (projectRoot == null || relativePath == null || relativePath.isBlank())
        {
            return;
        }

        File target = new File(projectRoot, relativePath);

        // Walk up until we find a folder that actually exists, in case
        // the saved path was deleted/renamed since it was last visited.
        while (!target.exists() && !target.equals(projectRoot))
        {
            target = target.getParentFile();
        }

        if (target == null || !target.exists())
        {
            return;
        }

        treeViewManager.expandAncestorsInTree(target);
        navigateTo(target);
    }

    /** Returns the current folder's path relative to projectRoot, or null if not under it. */
    public String getCurrentFolderRelativePath()
    {
        File projectRoot = treeViewManager.getProjectRoot();
        if (projectRoot == null || currentFolder == null) return null;
        try
        {
            return projectRoot.toPath().relativize(currentFolder.toPath()).toString();
        }
        catch (IllegalArgumentException e)
        {
            return null; // currentFolder isn't under projectRoot (shouldn't normally happen)
        }
    }
}