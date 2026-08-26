package com.lensora.lensorastudio.core.threading;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralised thread‑pool manager for the entire application.
 * Two dedicated pools:
 * - IO pool: user‑triggered, high‑priority operations (file copy, rename, delete).
 * - Background pool: low‑priority batch work (thumbnails, metadata extraction).
 * - Scheduler: periodic/delayed tasks (backup scheduler, watch service).
 * <p>
 * The background pool uses a bounded queue and a custom rejection handler
 * that cancels dropped tasks before retrying, ensuring that UI components
 * bound to {@code Task}s (like spinners) are properly cleaned up.
 */
public final class BackgroundExecutor 
{

    private static final Logger logger = LoggerFactory.getLogger(BackgroundExecutor.class);
    private static final BackgroundExecutor INSTANCE = new BackgroundExecutor();

    // High‑priority pool for user‑interactive tasks (oversized for blocking I/O)
    private final ExecutorService ioPool;

    // Low‑priority pool for background batch tasks (bounded queue + custom rejection)
    private final ThreadPoolExecutor backgroundPool;

    // Scheduled executor for delayed / periodic tasks
    private final ScheduledExecutorService scheduler;

    private BackgroundExecutor() 
    {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());

        // IO pool: slightly oversubscribed for blocking I/O (disk reads/writes)
        int ioThreads = Math.max(4, cores * 2);
        this.ioPool = Executors.newFixedThreadPool(ioThreads, new NamedThreadFactory("io-worker", Thread.NORM_PRIORITY));

        // Background pool: bounded queue with a rejection handler that cancels dropped tasks
        this.backgroundPool = new ThreadPoolExecutor(
                cores, cores * 2,
                30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new NamedThreadFactory("bg-worker", Thread.MIN_PRIORITY),
                (r, executor) -> {
                    if (!executor.isShutdown()) 
                    {
                        // Remove the oldest task from the queue
                        Runnable discarded = executor.getQueue().poll();
                        // Cancel it if it's a JavaFX Task or a Future
                        if (discarded instanceof Task<?> task) 
                        {
                            task.cancel(); // triggers onCancelled on FX thread
                            logger.debug("Background task cancelled due to queue overflow: {}", task);
                        } 
                        else if (discarded instanceof Future<?> future) 
                        {
                            future.cancel(true);
                            logger.debug("Future cancelled due to queue overflow");
                        }
                        // Retry the new task – queue now has one free slot
                        boolean accepted = executor.getQueue().offer(r);
                        if (!accepted) 
                        {
                            // In rare cases, if offer fails, fallback to a new thread (safe, but we log)
                            logger.warn("Could not offer task to background queue - executing on a temporary thread.");
                            new Thread(r, "Lensora-bg-fallback").start();
                        }
                    } 
                    else 
                    {
                        logger.warn("Background executor is shutting down - task rejected.");
                    }
                }
        );
        this.backgroundPool.allowCoreThreadTimeOut(true);

        // Scheduler: 2 threads
        this.scheduler = Executors.newScheduledThreadPool(2, new NamedThreadFactory("scheduler", Thread.NORM_PRIORITY));

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "Lensora-shutdown-hook"));
    }

    public static BackgroundExecutor getInstance() 
    {
        return INSTANCE;
    }

    // ─── IO Pool (high priority) ──────────────────────────────────────────

    public void executeIO(Runnable task) 
    {
        ioPool.execute(task);
    }

    /**
     * Submits a JavaFX Task to the IO pool. The Task is executed directly
     * (not wrapped) so cancellation works correctly.
     */
    public <T> void submitIO(Task<T> task) 
    {
        executeIO(task);
    }

    public Future<?> submitIO(Runnable task) 
    {
        return ioPool.submit(task);
    }

    public <T> Future<T> submitIO(Callable<T> task) 
    {
        return ioPool.submit(task);
    }

    // ─── Background Pool (low priority) ──────────────────────────────────

    public void executeBackground(Runnable task) 
    {
        backgroundPool.execute(task);
    }

    /**
     * Submits a JavaFX Task to the background pool. The Task is executed directly
     * (not wrapped) so the rejection handler can cancel it properly.
     */
    public <T> void submitBackground(Task<T> task) 
    {
        executeBackground(task);
    }

    public Future<?> submitBackground(Runnable task) 
    {
        return backgroundPool.submit(task);
    }

    public <T> Future<T> submitBackground(Callable<T> task) 
    {
        return backgroundPool.submit(task);
    }

    // ─── Scheduler ─────────────────────────────────────────────────────────

    public ScheduledFuture<?> scheduleOnce(Runnable task, long delay, TimeUnit unit) 
    {
        return scheduler.schedule(task, delay, unit);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) 
    {
        return scheduler.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    // ─── Shutdown ──────────────────────────────────────────────────────────

    public void shutdown() 
    {
        ioPool.shutdown();
        backgroundPool.shutdown();
        scheduler.shutdown();
        try 
        {
            if (!ioPool.awaitTermination(2, TimeUnit.SECONDS)) ioPool.shutdownNow();
            if (!backgroundPool.awaitTermination(2, TimeUnit.SECONDS)) backgroundPool.shutdownNow();
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) scheduler.shutdownNow();
        } 
        catch (InterruptedException e) 
        {
            ioPool.shutdownNow();
            backgroundPool.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("BackgroundExecutor shut down.");
    }

    // ─── Helper thread factory ────────────────────────────────────────────

    private static class NamedThreadFactory implements ThreadFactory 
    {
        private final String namePrefix;
        private final int priority;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String namePrefix, int priority) 
        {
            this.namePrefix = "Lensora-" + namePrefix + "-";
            this.priority = priority;
        }

        @Override
        public Thread newThread(Runnable r) 
        {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(priority);
            return t;
        }
    }
}