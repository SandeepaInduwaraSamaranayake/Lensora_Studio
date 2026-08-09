package com.lensora.lensorastudio.backup.verify;

import com.lensora.lensorastudio.backup.model.RestoreQueueItem;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.List;

/**
 * Verifies a set of RestoreQueueItems on a background thread, updating
 * each item's state/message live (via Platform.runLater, since these are
 * JavaFX properties bound to UI) and reporting overall progress -
 * verification hashes every file in the archive and can take real time
 * for large projects, so this must never run on the FX thread.
 */
public class BatchVerifyTask extends Task<Void>
{
    private final List<RestoreQueueItem> items;

    public BatchVerifyTask(List<RestoreQueueItem> items)
    {
        this.items = items;
    }

    @Override
    protected Void call()
    {
        int total = items.size();

        for (int i = 0; i < total; i++)
        {
            if (isCancelled()) break;

            RestoreQueueItem item = items.get(i);
            final int index = i;

            Platform.runLater(() -> item.setState(RestoreQueueItem.VerificationState.VERIFYING));
            updateMessage(String.format("Verifying %d of %d: %s", index + 1, total, item.getFile().getName()));
            updateProgress(index, total);

            var result = BackupVerifier.verify(item.getFile());

            Platform.runLater(() -> {
                item.setState(result.success()
                        ? RestoreQueueItem.VerificationState.VERIFIED_OK
                        : RestoreQueueItem.VerificationState.VERIFIED_FAILED);
                item.setMessage(result.message());
            });
        }

        updateProgress(total, total);
        updateMessage("Verification complete.");
        return null;
    }
}