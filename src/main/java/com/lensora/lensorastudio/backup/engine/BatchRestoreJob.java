package com.lensora.lensorastudio.backup.engine;

import javafx.concurrent.Task;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a RestoreJob per selected .lsbak file sequentially, aggregating
 * progress across the whole batch. Each archive is still restored into
 * its own independent destination subfolder - batching is purely UI/
 * orchestration convenience, matching BatchBackupJob's approach.
 */
public class BatchRestoreJob extends Task<List<Integer>>
{
    public record BatchItem(File lsbakFile, File destinationFolder) {}
    public record BatchItemResult(File lsbakFile, File destinationFolder, boolean succeeded,
                                    Integer newProjectId, String errorMessage) {}

    private final List<BatchItem> items;
    private final List<BatchItemResult> results = new ArrayList<>();

    public BatchRestoreJob(List<BatchItem> items)
    {
        this.items = items;
    }

    public List<BatchItemResult> getResults()
    {
        return results;
    }

    @Override
    protected List<Integer> call() throws Exception
    {
        List<Integer> newProjectIds = new ArrayList<>();
        int total = items.size();

        for (int i = 0; i < total; i++)
        {
            if (isCancelled()) break;

            BatchItem item = items.get(i);
            final int index = i;

            updateMessage(String.format("Restoring %d of %d: %s",
                    index + 1, total, item.lsbakFile().getName()));

            RestoreJob singleJob = new RestoreJob(item.lsbakFile(), item.destinationFolder());

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
                int newProjectId = singleJob.runSynchronously();
                newProjectIds.add(newProjectId);
                results.add(new BatchItemResult(item.lsbakFile(), item.destinationFolder(), true, newProjectId, null));
            }
            catch (Exception e)
            {
                results.add(new BatchItemResult(item.lsbakFile(), item.destinationFolder(), false, null, e.getMessage()));
                // Continue with remaining archives rather than aborting the whole batch.
            }
        }

        updateProgress(1, 1);
        updateMessage(String.format("Batch restore complete: %d/%d succeeded.",
                newProjectIds.size(), total));
        return newProjectIds;
    }
}