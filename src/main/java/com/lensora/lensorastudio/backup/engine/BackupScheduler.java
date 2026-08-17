package com.lensora.lensorastudio.backup.engine;

import com.lensora.lensorastudio.backup.model.BackupHistoryItem;
import com.lensora.lensorastudio.backup.model.BackupSchedule;
import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.repository.BackupHistoryRepository;
import com.lensora.lensorastudio.repository.BackupScheduleRepository;
import com.lensora.lensorastudio.repository.ProjectRepository;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Background scheduler: ticks every minute, checks every enabled
 * BackupSchedule's next_run, and triggers a BatchBackupJob for any that
 * are due - entirely independent of whether the user is actively using
 * the app. Runs for the lifetime of the JVM process (started in
 * App.start(), stopped on shutdown).
 */
public final class BackupScheduler
{
    private static final Logger logger = LoggerFactory.getLogger(BackupScheduler.class);
    private static BackupScheduler instance;

    private ScheduledExecutorService executor;

    /** Set by MainController once the UI exists, so triggered jobs can surface a toast + status-bar progress. */
    private BiConsumer<String, Task<?>> onJobTriggered;

    /** For live schedule status in the Schedule table  */
    private final Map<Integer, BackupSchedule.RunStatus> liveStatus = new ConcurrentHashMap<>();
    private final List<Runnable> statusChangeListeners = new CopyOnWriteArrayList<>();


    private BackupScheduler() {}

    public static synchronized BackupScheduler getInstance()
    {
        if (instance == null) instance = new BackupScheduler();
        return instance;
    }

    public void setOnJobTriggered(BiConsumer<String, Task<?>> callback)
    {
        this.onJobTriggered = callback;
    }

    public void start()
    {
        if (executor != null && !executor.isShutdown()) return;

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Lensora-backup-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Check every minute - coarse enough to be cheap, fine enough
        // that hourly/daily/weekly schedules trigger within a minute of
        // their target time.
        executor.scheduleAtFixedRate(this::checkDueSchedules, 0, 1, TimeUnit.MINUTES);
        logger.info("[BackupScheduler] Started.");
    }

    public void stop()
    {
        if (executor != null)
        {
            executor.shutdownNow();
            logger.info("[BackupScheduler] Stopped.");
        }
    }

    private void checkDueSchedules()
    {
        try
        {
            List<BackupSchedule> schedules = BackupScheduleRepository.findEnabled();
            LocalDateTime now = LocalDateTime.now();

            for (BackupSchedule schedule : schedules)
            {
                if (schedule.getNextRun() == null)
                {
                    // First-time schedule with no next_run computed yet.
                    schedule.setNextRun(computeNextRun(schedule, now));
                    BackupScheduleRepository.update(schedule);
                    continue;
                }

                if (!now.isBefore(schedule.getNextRun()))
                {
                    runSchedule(schedule);
                }
                else
                {
                    // Not due yet but enabled - reflect as "Scheduled" if not already running.
                    if (getLiveStatus(schedule.getScheduleId()) != BackupSchedule.RunStatus.RUNNING)
                    {
                        setLiveStatus(schedule.getScheduleId(), BackupSchedule.RunStatus.SCHEDULED);
                    }
                }
            }
        }
        catch (SQLException e)
        {
            logger.error("[BackupScheduler] Failed to check schedules", e);
        }
    }

    private void runSchedule(BackupSchedule schedule)
    {
        logger.info("[BackupScheduler] Triggering schedule: {}", schedule.getName());
        setLiveStatus(schedule.getScheduleId(), BackupSchedule.RunStatus.RUNNING);

        try
        {
            advanceSchedule(schedule);   // sets nextRun to the next scheduled time

            List<Project> targets = resolveTargetProjects(schedule);
            if (targets.isEmpty())
            {
                logger.warn("[BackupScheduler] Schedule '{}' has no matching projects - skipping.", schedule.getName());
                setLiveStatus(schedule.getScheduleId(), BackupSchedule.RunStatus.IDLE);
                advanceSchedule(schedule);
                return;
            }

            File destinationFolder = new File(schedule.getDestinationPath());
            if (!destinationFolder.exists() && !destinationFolder.mkdirs())
            {
                logger.error("[BackupScheduler] Could not create/access destination for '{}': {}",
                        schedule.getName(), schedule.getDestinationPath());
                setLiveStatus(schedule.getScheduleId(), BackupSchedule.RunStatus.FAILED);
                advanceSchedule(schedule);
                return;
            }

            List<BatchBackupJob.BatchItem> items = new ArrayList<>();
            for (Project project : targets)
            {
                items.add(new BatchBackupJob.BatchItem(project,
                        new File(destinationFolder, BackupService.suggestFileName(project))));
            }

            BatchBackupJob job = BackupService.createBatchBackupJob(items);

            job.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> 
                {
                    long failed = job.getResults().stream().filter(r -> !r.succeeded()).count();
                    setLiveStatus(schedule.getScheduleId(),
                            failed == 0 ? BackupSchedule.RunStatus.SUCCEEDED : BackupSchedule.RunStatus.FAILED);
                    recordHistoryForSchedule(schedule, job);
                    updateLastRun(schedule);
                });

            job.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> 
                {
                    setLiveStatus(schedule.getScheduleId(), BackupSchedule.RunStatus.FAILED);
                    updateLastRun(schedule);
                });

            job.addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, e -> 
                {
                    setLiveStatus(schedule.getScheduleId(), BackupSchedule.RunStatus.IDLE);
                    updateLastRun(schedule);
                });

            if (onJobTriggered != null)
            {
                // Hands the job to the UI (status bar progress + toast). If
                // the UI isn't ready yet (very early startup), fall back
                // to running headless.
                Platform.runLater(() -> onJobTriggered.accept(schedule.getName(), job));
            }

            Thread thread = new Thread(job, "Lensora-scheduled-backup: Schedule ID - " + schedule.getScheduleId());
            thread.setDaemon(true);
            thread.start();
        }
        catch (Exception e)
        {
            logger.error("[BackupScheduler] Failed to run schedule '{}'", schedule.getName(), e);
            setLiveStatus(schedule.getScheduleId(), BackupSchedule.RunStatus.FAILED);
            advanceSchedule(schedule);
        }
    }

    // Helper to update lastRun
    private void updateLastRun(BackupSchedule schedule) 
    {
        Executors.newSingleThreadExecutor().submit(() -> {
            try
            {
                schedule.setLastRun(LocalDateTime.now());
                BackupScheduleRepository.update(schedule);
            } 
            catch (SQLException e)
            {
                logger.error("[BackupScheduler] Failed to update lastRun for '{}'", schedule.getName(), e);
            }
        });
    }

    private void advanceSchedule(BackupSchedule schedule)
    {
        try
        {
            schedule.setLastRun(LocalDateTime.now());
            schedule.setNextRun(computeNextRun(schedule, LocalDateTime.now()));
            BackupScheduleRepository.update(schedule);
        }
        catch (SQLException e)
        {
            logger.error("[BackupScheduler] Failed to advance schedule '{}'", schedule.getName(), e);
        }
    }

    private List<Project> resolveTargetProjects(BackupSchedule schedule) throws SQLException
    {
        return switch (schedule.getScope())
        {
            case ALL -> ProjectRepository.findAll();
            case SINGLE, MULTIPLE ->
            {
                List<Project> all = ProjectRepository.findAll();
                List<Integer> ids = schedule.getProjectIds();
                yield all.stream().filter(p -> ids != null && ids.contains(p.getProjectId())).toList();
            }
        };
    }

    /** Computes the next trigger time from "now", based on frequency/interval/time-of-day/day-of-week. */
    public static LocalDateTime computeNextRun(BackupSchedule schedule, LocalDateTime from)
    {
        return switch (schedule.getFrequency())
        {
            case HOURLY -> from.plusHours(Math.max(1, schedule.getIntervalValue()));

            case DAILY ->
            {
                LocalTime time = parseTimeOrDefault(schedule.getTimeOfDay());
                LocalDateTime candidate = from.toLocalDate().atTime(time);
                if (!candidate.isAfter(from))
                {
                    candidate = candidate.plusDays(Math.max(1, schedule.getIntervalValue()));
                }
                yield candidate;
            }

            case WEEKLY ->
            {
                LocalTime time = parseTimeOrDefault(schedule.getTimeOfDay());
                int targetDow = schedule.getDayOfWeek() != null ? schedule.getDayOfWeek() : 1;
                LocalDateTime candidate = from.toLocalDate().atTime(time);
                while (candidate.getDayOfWeek().getValue() != targetDow || !candidate.isAfter(from))
                {
                    candidate = candidate.plusDays(1);
                }
                yield candidate;
            }
        };
    }

    private static LocalTime parseTimeOrDefault(String hhmm)
    {
        try
        {
            return hhmm != null ? LocalTime.parse(hhmm) : LocalTime.of(2, 0);
        }
        catch (Exception e)
        {
            return LocalTime.of(2, 0);
        }
    }

    // ------------------------- Live schedule status ----------------------------
    public void addStatusChangeListener(Runnable listener)
    {
        statusChangeListeners.add(listener);
    }

    public void removeStatusChangeListener(Runnable listener)
    {
        statusChangeListeners.remove(listener);
    }

    public BackupSchedule.RunStatus getLiveStatus(int scheduleId)
    {
        return liveStatus.getOrDefault(scheduleId, BackupSchedule.RunStatus.IDLE);
    }

    private void setLiveStatus(int scheduleId, BackupSchedule.RunStatus status)
    {
        liveStatus.put(scheduleId, status);
        Platform.runLater(() -> {
            for (Runnable listener : statusChangeListeners) listener.run();
        });
    }

    // ------------------------ Record Backup History ----------------------------
    private void recordHistoryForSchedule(BackupSchedule schedule, BatchBackupJob job)
    {
        for (var result : job.getResults())
        {
            try
            {
                BackupHistoryItem item = new BackupHistoryItem();
                item.setScheduleId(schedule.getScheduleId());
                item.setScheduleName(schedule.getName());
                item.setProjectId(result.project().getProjectId());
                item.setProjectNumber(result.project().getProjectNumber());
                item.setClientName(result.project().getClientName());
                item.setFilePath(result.file() != null ? result.file().getAbsolutePath() : result.destinationFile().getAbsolutePath());
                item.setFileSize(result.file() != null && result.file().exists() ? result.file().length() : 0);
                item.setStatus(result.succeeded() ? "SUCCEEDED" : "FAILED");
                item.setErrorMessage(result.errorMessage());
                item.setStartedAt(LocalDateTime.now());
                item.setCompletedAt(LocalDateTime.now());
                BackupHistoryRepository.insert(item);
            }
            catch (SQLException e)
            {
                logger.error("[BackupScheduler] Failed to record backup history", e);
            }
        }
    }
}