package pt.um.ucl.positioning.C03a.uwb.communications;

import pt.um.ucl.positioning.C03a.uwb.devices.Tag;
import pt.um.ucl.positioning.C03a.uwb.config.Config;
import pt.um.ucl.positioning.C03a.uwb.database.MeasurementsDatabaseLogger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages a fixed thread pool (ExecutorService) for handling asynchronous output tasks.
 * <p>
 * This class decouples the main servlet thread ({@link C03a}) from the (potentially slow)
 * work of database logging and HTTP posting. It accepts batches of {@link Tag} objects
 * and submits a new {@link OutputTask} for each tag to the thread pool.
 * 
 * @author Gustavo Oliveira
 * @version 0.6
 */
public class OutputThread {

    /** The size of the fixed thread pool. */
    private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors() * 8;
    
    /** The executor service that runs the output tasks. */
    private final ExecutorService executorService = Executors.newFixedThreadPool(POOL_SIZE);
    /** The database logger instance. */
    private final MeasurementsDatabaseLogger dbLogger;
    /** System configuration. */
    private final Config config;
    /** Servelet context. */
    private final C03a context;

    /**
     * Constructs a new OutputThread manager.
     *
     * @param endpointUrl The URL of the Position Estimator service.
     * @param dbLogger The shared {@link MeasurementsDatabaseLogger} instance.
     * @param exportToDbQ {@code true} to enable database logging.
     * @param exportToPeQ {@code true} to enable posting to the Position Estimator.
     * @param token The authentication token for the Position Estimator.
     */
    public OutputThread(C03a context, MeasurementsDatabaseLogger dbLogger, Config config) {
    	this.context = context;
        this.dbLogger = dbLogger;
        this.config = config;
    }
    
    /**
     * Submits a batch of tags for processing.
     * <p>
     * For each tag in the list, a new {@link OutputTask} is created and
     * submitted to the thread pool for asynchronous execution.
     *
     * @param tags A list of {@link Tag} objects, each containing a
     * completed measurement to be processed.
     */
    public void submitTagBatch(List<Tag> tags) {
        for (Tag tag : tags) {
            executorService.submit(new OutputTask(context, tag, dbLogger, this.config));
        }
    }


    /**
     * Initiates a graceful shutdown of the thread pool.
     * <p>
     * It waits for a fixed period for tasks to complete. If tasks do not
     * complete within the timeout, it forces a shutdown.
     */
    public void shutdown() {
        System.out.println("Shutting down output thread pool...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if(this.config.isEnableGeneralLogs()) System.err.println("Output pool did not shut down cleanly. Some tasks were aborted.");
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}