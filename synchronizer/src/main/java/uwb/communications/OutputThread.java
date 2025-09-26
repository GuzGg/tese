package uwb.communications;

import uwb.devices.Tag;
import uwb.database.MeasurementsDatabaseLogger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OutputThread { // Acts as the Output Manager

    // Determine pool size based on the I/O-bound nature of the task
    // Using 8x available processors as a robust starting point for I/O tasks.
    private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors() * 8;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(POOL_SIZE);
    private final String endpointUrl;
    private final MeasurementsDatabaseLogger dbLogger;

    public OutputThread(String endpointUrl, MeasurementsDatabaseLogger dbLogger) {
        this.endpointUrl = endpointUrl;
        this.dbLogger = dbLogger;
        System.out.println("Initialized Output Thread Pool with " + POOL_SIZE + " workers.");
    }
    
    /**
     * Submits a batch of tag measurements to the executor service.
     * This method is fast and non-blocking.
     */
    public void submitTagBatch(List<Tag> tags) {
        for (Tag tag : tags) {
            // Each tag's measurement processing is submitted as a separate task
            executorService.submit(new OutputTask(tag, endpointUrl, dbLogger));
        }
    }

    /**
     * Gracefully shuts down the thread pool, ensuring all submitted tasks are completed.
     * Should be called from the servlet's destroy() method.
     */
    public void shutdown() {
        System.out.println("Shutting down output thread pool...");
        executorService.shutdown();
        try {
            // Wait up to 30 seconds for current tasks to finish
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // Force shutdown if wait limit is hit
                System.err.println("Output pool did not shut down cleanly. Some tasks were aborted.");
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt(); // Restore interrupt status
        }
    }
}