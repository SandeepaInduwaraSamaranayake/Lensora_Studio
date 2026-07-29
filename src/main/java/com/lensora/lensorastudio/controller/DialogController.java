package com.lensora.lensorastudio.controller;

/**
 * Optional contract for dialog controllers.
 *
 * The header/title-bar/close-button are now built by DialogBuilder itself,
 * so implementing this interface is no longer required just to get a
 * draggable window. Implement it only if the controller needs to:
 *   - supply a custom icon/title (via getDialogIcon/getDialogTitle), or
 *   - intercept close (e.g. block closing while unsaved changes exist).
 *
 * All methods have defaults, so implementing zero, one, or all of them is fine.
 */
public interface DialogController 
{
    /** Return false to block the close button / window-close request. */
    default boolean canClose()
    {
        return true;
    }

    /** Called right after the close is confirmed and the stage is about to hide. */
    default void onClosing()
    {
        
    }
}
