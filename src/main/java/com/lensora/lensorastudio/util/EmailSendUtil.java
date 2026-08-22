package com.lensora.lensorastudio.util;

import javafx.scene.Node;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;
import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;

public final class EmailSendUtil
{
    private static final Logger logger = LoggerFactory.getLogger(EmailSendUtil.class);

    private static final String SUBJECT_TEMPLATE = "Files from Lensora Studio";
    private static final String BODY_TEMPLATE = """
            ==================================================
                        L E N S O R A   S T U D I O
            ==================================================

            Please find the attached file(s).

            --------------------------------------------------
            Sent using Lensora Studio
            Website: https://lensorastudio.com
            """;

    private EmailSendUtil() {}

    public static void sendFiles(List<File> files, Node targetContainer)
    {
        if (files == null || files.isEmpty()) return;

        try
        {
            String subject = URLEncoder.encode(SUBJECT_TEMPLATE, StandardCharsets.UTF_8).replace("+", "%20");
            String body = URLEncoder.encode(buildBody(files), StandardCharsets.UTF_8).replace("+", "%20");

            URI mailto = URI.create("mailto:?subject=" + subject + "&body=" + body);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL))
            {
                Desktop.getDesktop().mail(mailto);
                NotificationUtil.showToast(targetContainer, "Mail app opened. Drag files from Explorer to attach.");
            }
            else
            {
                logger.warn("[EmailSendUtil] Desktop mail action not supported on this platform.");
                NotificationUtil.showToast(targetContainer, "No default email client found on this system", "fas-exclamation-circle");
                return;
            }

            revealFilesInExplorer(files);
        }
        catch (Exception e)
        {
            logger.error("[EmailSendUtil] Failed to open email client", e);
            ErrorHandler.show(null, "Send via Email failed", e);
        }
    }

    private static String buildBody(List<File> files)
    {
        StringBuilder sb = new StringBuilder(BODY_TEMPLATE);
        if (files.size() > 1)
        {
            sb.append("\n\nFile(s) to attach (").append(files.size()).append("):\n");
            for (File f : files)
            {
                sb.append("  • ").append(f.getName()).append("\n");
            }
        }
        return sb.toString();
    }

    private static void revealFilesInExplorer(List<File> files)
    {
        if (!Desktop.isDesktopSupported()) return;
        Desktop desktop = Desktop.getDesktop();

        File first = files.get(0);
        try
        {
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR))
            {
                desktop.browseFileDirectory(first);
            }
            else if (first.getParentFile() != null && desktop.isSupported(Desktop.Action.OPEN))
            {
                desktop.open(first.getParentFile());
            }
        }
        catch (IOException e)
        {
            logger.debug("[EmailSendUtil] Could not reveal files in explorer", e);
        }
    }
}