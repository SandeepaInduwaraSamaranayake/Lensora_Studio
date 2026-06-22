package com.lensora.lensorastudio.controller;

import javafx.scene.Node;

/**
 * Contract for dialog controllers that provide a draggable header.
 * Implement this to allow the {@link DialogBuilder} to set up window dragging
 * without using string‑based lookups.
 */
public interface DialogController 
{
    /**
     * @return the {@link Node} (typically an {@link HBox}) that should be draggable
     */
    Node getHeaderNode();
}
