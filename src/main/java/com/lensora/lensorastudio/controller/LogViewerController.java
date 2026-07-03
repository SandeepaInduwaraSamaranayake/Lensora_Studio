package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.util.Dialogs;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LogViewerController implements DialogController 
{
    private static final Logger logger = LoggerFactory.getLogger(LogViewerController.class);

    @FXML private HBox logHeaderBar;
    @FXML private Label logPathLabel;
    @FXML private Label logSizeLabel;
    @FXML private TextArea logTextArea;
    @FXML private Button refreshButton;
    @FXML private Button copyButton;
    @FXML private Button closeButton;
    @FXML private Button closeButtonBottom;

    private Stage stage;

    @Override
    public Node getHeaderNode() 
    {
        return logHeaderBar;
    }

    @FXML
    public void initialize() 
    {
        logger.info("[LogViewerController] Initializing LogViewerController...");
        loadLogFile();
    }

    private void loadLogFile() 
    {
        String logDir  = AppSettings.getInstance().getDefaultLogDir();
        Path   logPath = Paths.get(logDir, "app.log");
        File   logFile = logPath.toFile();

        logPathLabel.setText(logPath.toString());

        if (!logFile.exists() || !logFile.isFile()) 
        {
            logTextArea.setText("Log file not found: " + logPath);
            logSizeLabel.setText("0 bytes");
            return;
        }

        try 
        {
            long size = logFile.length();
            logSizeLabel.setText(formatFileSize(size));

            // Read the whole file; for large logs this could be heavy, but logs are usually small.
            String content = Files.readString(logPath);
            logTextArea.setText(content);

            // Move caret to the end to show latest logs
            logTextArea.end();
        } 
        catch (IOException e) 
        {
            logger.error("Failed to read log file", e);
            logTextArea.setText("Error reading log file: " + e.getMessage());
            logSizeLabel.setText("Error");
        }
    }

    @FXML
    private void handleRefresh() 
    {
        loadLogFile();
    }

    @FXML
    private void handleCopy() 
    {
        String content = logTextArea.getText();
        if (content == null || content.isEmpty()) 
        {
            Dialogs.showInfo(stage, "Copy", "Nothing to copy", "The log is empty.");
            return;
        }
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(content);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
        Dialogs.showInfo(stage, "Copy", "Copied!", "Log content copied to clipboard.");
    }

    @FXML
    private void handleClose() 
    {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    public void setStage(Stage stage) 
    {
        this.stage = stage;
    }

    private String formatFileSize(long size) 
    {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }
}