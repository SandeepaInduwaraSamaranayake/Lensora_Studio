package com.lensora.lensorastudio.backup.engine;

import com.lensora.lensorastudio.model.Project;

import javafx.concurrent.Task;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a BackupJob per project sequentially, aggregating progress across
 * the whole batch. Each project still produces its own independent
 * .lsbak file - batching is purely a UI/orchestration convenience, not a
 * combined-archive format, so every resulting file stays individually
 * shareable/restorable exactly like a single-project backup.
 */
public class BatchBackupJob extends Task<List<File>>
{
    public record BatchItem(Project project, File destinationFile) {}
    public record BatchItemResult(Project project, File destinationFile, File file, boolean succeeded, String errorMessage) {}

    private final List<BatchItem> items;
    private final List<BatchItemResult> results = new ArrayList<>();

    public BatchBackupJob(List<BatchItem> items)
    {
        this.items = items;
    }

    public List<BatchItemResult> getResults()
    {
        return results;
    }

    @Override
    protected List<File> call() throws Exception
    {
        List<File> completedFiles = new ArrayList<>();
        int total = items.size();

        for (int i = 0; i < total; i++)
        {
            if (isCancelled()) break;

            BatchItem item = items.get(i);
            final int index = i;

            updateMessage(String.format("Backing up project %d of %d: %s",
                    index + 1, total, item.project().getProjectNumber()));

            BackupJob singleJob = new BackupJob(item.project(), item.destinationFile());

            // Bridge the sub-job's own progress into the batch's overall
            // progress: each project occupies an equal 1/total slice.
            singleJob.progressProperty().addListener((obs, old, val) -> {
                double subProgress = val.doubleValue() < 0 ? 0 : val.doubleValue();
                double overall = (index + subProgress) / total;
                updateProgress(overall, 1.0);
            });
            singleJob.messageProperty().addListener((obs, old, val) -> {
                updateMessage(String.format("[%d/%d] %s", index + 1, total, val));
            });

            try
            {
                // Run the sub-job's work synchronously on THIS background
                // thread (not via a new Thread) - BatchBackupJob is
                // already off the FX thread, so this keeps execution
                // strictly sequential without extra thread management.
                File result = singleJob.runSynchronously();
                completedFiles.add(result);
                results.add(new BatchItemResult(item.project(), item.destinationFile(), result, true, null));
            }
            catch (Exception e)
            {
                results.add(new BatchItemResult(item.project(), item.destinationFile(), null, false, e.getMessage()));
                // Continue with remaining projects rather than aborting
                // the whole batch on one failure.
            }
        }

        updateProgress(1, 1);
        updateMessage(String.format("Batch backup complete: %d/%d succeeded.",
                completedFiles.size(), total));
        return completedFiles;
    }
}