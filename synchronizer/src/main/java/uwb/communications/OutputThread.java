package uwb.communications;

import uwb.devices.Tag;
import uwb.database.MeasurementsDatabaseLogger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OutputThread {

    private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors() * 8;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(POOL_SIZE);
    private final String endpointUrl;
    private final MeasurementsDatabaseLogger dbLogger;
    private final boolean exportToDbQ;
    private final boolean exportToPeQ;
    private final String token;

    public OutputThread(String endpointUrl, MeasurementsDatabaseLogger dbLogger, boolean exportToDbQ, boolean exportToPeQ, String token) {
        this.endpointUrl = endpointUrl;
        this.dbLogger = dbLogger;
        this.exportToDbQ = exportToDbQ;
        this.exportToPeQ = exportToPeQ;
        this.token = token;
        System.out.println("Initialized Output Thread Pool with " + POOL_SIZE + " workers.");
    }
    
    public void submitTagBatch(List<Tag> tags) {
        for (Tag tag : tags) {
            executorService.submit(new OutputTask(tag, endpointUrl, dbLogger, this.exportToDbQ, this.exportToPeQ, token));
        }
    }


    public void shutdown() {
        System.out.println("Shutting down output thread pool...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                System.err.println("Output pool did not shut down cleanly. Some tasks were aborted.");
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}