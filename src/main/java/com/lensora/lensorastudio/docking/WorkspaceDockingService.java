package com.lensora.lensorastudio.docking;

import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.stage.Stage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snapfx.DockUserAgentThemeMode;
import org.snapfx.SnapFX;
import org.snapfx.model.DockElement;
import org.snapfx.model.DockNode;
import org.snapfx.model.DockPosition;
import org.snapfx.model.DockSplitPane;

import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.services.ThemeManager;

public class WorkspaceDockingService
{
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceDockingService.class);

    private final Map<String, DockNode> nodes = new LinkedHashMap<>();
    private final Map<String, String> titles = new LinkedHashMap<>();
    private final SnapFX snapFX = new SnapFX();

    private DockNode projectsNode;
    private DockNode detailsNode;
    private DockNode filesNode;
    private DockNode metadataNode; 

    private Runnable onRebuildRequested;
    private boolean layoutInitialized = false;
    private Runnable onNodeVisibilityChanged;

    public WorkspaceDockingService()
    {
        // Ask SnapFX to pick its correct internal stylesheet itself —
        // no custom CSS, no jar patching needed for either theme family.
        snapFX.setUserAgentThemeMode(DockUserAgentThemeMode.AUTO);
        snapFX.setDefaultCloseBehavior(org.snapfx.close.DockCloseBehavior.HIDE);
    }

    public void initialize(Stage stage)
    {
        snapFX.initialize(stage);
        snapFX.setOnCloseHandled(result -> {
            if (onNodeVisibilityChanged != null)
            {
                onNodeVisibilityChanged.run();
            }
        });

        logger.info("SnapFX initialized");
    }

    public void register(String id, Node content, String title)
    {
        DockNode dockNode = new DockNode(id, content, title);
        nodes.put(id, dockNode);
        titles.put(id, title);

        switch (id)
        {
            case "projects" -> projectsNode = dockNode;
            case "projectDetails" -> detailsNode = dockNode;
            case "files" -> filesNode = dockNode;
            case "metadata" -> metadataNode = dockNode;
        }
    }


    public void setOnNodeVisibilityChanged(Runnable callback)
    {
        this.onNodeVisibilityChanged = callback;
    }

    public Map<String, String> getRegisteredPanels()
    {
        return Collections.unmodifiableMap(titles);
    }

    // ─── Theme integration ──────────────────────────────────────────────────

    /**
     * Call this every time the app-wide JavaFX theme changes (ThemeManager.applyTheme).
     * Explicit mode is more reliable than AUTO's heuristic, since we already
     * know definitively whether the active theme is AtlantaFX-based.
     */
    public void syncTheme(boolean atlantaFxBased)
    {
        snapFX.setUserAgentThemeMode(
                atlantaFxBased ? DockUserAgentThemeMode.ATLANTAFX_COMPAT : DockUserAgentThemeMode.MODENA);
        snapFX.refreshUserAgentThemeIntegration();
        logger.info("[Docking] SnapFX theme mode set to {}",
                atlantaFxBased ? "ATLANTAFX_COMPAT" : "MODENA");
    }

    // ─── Visibility ─────────────────────────────────────────────────────────

    /** Ground truth: a node is visible iff it's actually attached in the live DockGraph. */
    public boolean isVisible(String id)
    {
        DockNode node = nodes.get(id);
        return node != null && node.getParent() != null;
    }

    public void hidePanel(String id)
    {
        DockNode node = nodes.get(id);
        if (node == null || !isVisible(id)) return;

        snapFX.hide(node); // SnapFX undocks it AND remembers lastKnownTarget/Position internally
        rebuildAndRemount();
        saveLayout();
    }

    public void showPanel(String id)
    {
        DockNode node = nodes.get(id);
        if (node == null || isVisible(id)) return;

        if (node.getLastKnownTarget() != null)
        {
            // SnapFX remembers exactly where this was docked before — use it.
            snapFX.restore(node);
        }
        else
        {
            // Never docked this session (e.g. the saved layout JSON didn't
            // include it at startup) — fall back to a sensible default spot.
            dockToDefaultPosition(node);
            saveLayout(); 
        }

        rebuildAndRemount();
    }

    /** Default placement for a panel that has no remembered dock location yet. */
    private void dockToDefaultPosition(DockNode node)
    {
        String id = node.getId();

        DockElement target;
        DockPosition position;

        if ("projects".equals(id))
        {
            snapFX.getDockGraph().setRoot(node);
            return;
        }
        else if ("projectDetails".equals(id))
        {
            target = isVisible("projects") ? projectsNode : null;
            position = DockPosition.RIGHT;
        }
        else if ("metadata".equals(id)) 
        {
            // Dock as a tab in the details panel if visible, else to the right of projects
            if (isVisible("projectDetails")) 
            {
                snapFX.dock(node, detailsNode, DockPosition.CENTER);
            } 
            else if (isVisible("projects")) 
            {
                snapFX.dock(node, projectsNode, DockPosition.RIGHT);
            } 
            else
            {
                snapFX.getDockGraph().setRoot(node);
            }
            return;
        }
        else // "files"
        {
            target = isVisible("projectDetails") ? detailsNode
                    : isVisible("projects") ? projectsNode
                    : null;
            position = DockPosition.RIGHT;
        }

        if (target == null || target.getParent() == null)
        {
            // Nothing else visible to dock next to — become root.
            snapFX.getDockGraph().setRoot(node);
        }
        else
        {
            snapFX.dock(node, target, position);
        }
    }

    public void setOnRebuildRequested(Runnable callback)
    {
        this.onRebuildRequested = callback;
    }

    private void rebuildAndRemount()
    {
        if (onRebuildRequested != null) onRebuildRequested.run();
    }

    // ─── Layout building ────────────────────────────────────────────────────

    public Parent buildLayout()
    {
        snapFX.closeFloatingWindows(true);
        configureNodeFactory();

        if (!layoutInitialized)
        {
            String savedLayout = AppSettings.getInstance().getDockLayout();
            if (savedLayout == null || savedLayout.isBlank())
            {
                createDefaultLayout();
            }
            else
            {
                try
                {
                    snapFX.loadLayout(savedLayout);
                    logger.info("Dock layout restored");
                }
                catch (Exception e)
                {
                    logger.error("Failed restoring layout", e);
                    createDefaultLayout();
                }
            }
            layoutInitialized = true;
        }

        logger.info("Dock layout built. Root = {}", snapFX.getDockGraph().getRoot());
        return snapFX.buildLayout();
    }

    public void createDefaultLayout()
    {
        DockSplitPane left = new DockSplitPane(Orientation.VERTICAL);
        left.addChild(projectsNode);
        left.addChild(detailsNode);
        left.setDividerPosition(0, 0.50);      // projects 50%  details 50%

        // Right side: horizontal split with files on left, metadata on right
        DockSplitPane right = new DockSplitPane(Orientation.HORIZONTAL);
        right.addChild(filesNode);
        right.addChild(metadataNode);
        right.setDividerPosition(0, 0.80);     // files get 80% metadata 20%

        // Root: horizontal split with left and right
        DockSplitPane root = new DockSplitPane(Orientation.HORIZONTAL);
        root.addChild(left);
        root.addChild(right);
        root.setDividerPosition(0, 0.20);

        snapFX.getDockGraph().setRoot(root);
        logger.info("Created default dock layout");
    }

    public void saveLayout()
    {
        String json = snapFX.saveLayout();
        AppSettings.getInstance().setDockLayout(json);
        logger.info("Saved dock layout");
    }

    private void configureNodeFactory()
    {
        snapFX.setNodeFactory(id -> {
            DockNode node = nodes.get(id);
            if (node == null) logger.warn("Cannot restore dock node: {}", id);
            return node;
        });
    }

    public void registerThemeListener()
    {
        ThemeManager.setThemeChangeListener(theme -> {
            boolean isAtlantaFx =   theme == AppSettings.Theme.CUPERTINO_DARK  ||
                                    theme == AppSettings.Theme.CUPERTINO_LIGHT ||
                                    theme == AppSettings.Theme.NORD_DARK       ||
                                    theme == AppSettings.Theme.PRIMER_DARK     ||
                                    theme == AppSettings.Theme.PRIMER_LIGHT;
            syncTheme(isAtlantaFx);
        });
    }

    // ─── Layout locking ─────────────────────────────────────────────────────

    public boolean isLocked()
    {
        return snapFX.isLocked();
    }

    public void setLocked(boolean locked)
    {
        snapFX.setLocked(locked);
        logger.info("[Docking] Layout {}", locked ? "locked" : "unlocked");
    }

    public void toggleLocked()
    {
        setLocked(!isLocked());
    }

    public DockNode getNode(String id) 
    {
        return nodes.get(id);
    }

    public SnapFX getSnapFX()
    {
        return snapFX;
    }
}