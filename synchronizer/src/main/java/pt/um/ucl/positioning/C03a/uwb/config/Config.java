package pt.um.ucl.positioning.C03a.uwb.config;

import java.util.Properties;

/**
 * A configuration class that holds all application settings.
 * <p>
 * This class loads its values from a {@link Properties} object upon
 * construction and provides read-only access to these settings via
 * getter methods. It centralizes configuration for the database,
 * action manager, export flags, and position estimator.
 * 
 * @author Gustavo Oliveira
 * @version 0.1
 */
public class Config {

    // --- Database Properties ---
    /** The JDBC URL for the database connection (e.g., "jdbc:mysql://localhost:3306"). */
    private final String dbUrl;
    /** The username for the database. */
    private final String dbUsername;
    /** The password for the database. */
    private final String dbPassword;
    /** The specific name of the database/schema to use. */
    private final String dbName;

    // --- Action Manager Properties ---
    /** The time period between slow scans (milliseconds). */
    private final long amSlowScanPeriod;
    /** The time period after which a new fast scan is triggered (milliseconds). */
    private final long amFastScanPeriod;
    /** The minimum time interval between scans (milliseconds). */
    private final long amScanInterval;
    /** The duration of a single scan operation (milliseconds). */
    private final long amScanTime;

    // --- Export Flags ---
    /** Flag to enable or disable exporting data to the Database Queue. */
    private final boolean exportToDbQ;
    /** Flag to enable or disable exporting data to the Position Estimator Queue. */
    private final boolean exportToPeQ;
    
    // --- Log Flags ---
    /** Flags to enable or disable Logs. */
    private final boolean enableInputLogs;
    private final boolean enableOutputLogs;
    private final boolean enableGeneralLogs;
   
    // --- Position Estimator Properties ---
    /** The URL of the Position Estimator service. */
    private final String peUrl;
    /** The authentication token (e.g., Bearer token) for the Position Estimator service. */
    private final String peToken;
    
    private final int dbMaxRetries;
    private final int dbRetryDelay;

    /**
     * Constructs a new Config object by parsing properties from a {@link Properties} object.
     *
     * @param props The {@link Properties} object containing all necessary configuration keys.
     */
    public Config(Properties props) {
        // Database
        this.dbUrl = props.getProperty("db.url");
        this.dbUsername = props.getProperty("db.username");
        this.dbPassword = props.getProperty("db.password");
        this.dbName = props.getProperty("db.name");

        // Action Manager
        this.amSlowScanPeriod = Long.parseLong(props.getProperty("am.slowScanPeriod"));
        this.amFastScanPeriod = Long.parseLong(props.getProperty("am.fastScanPeriod"));
        this.amScanInterval = Long.parseLong(props.getProperty("am.scanInterval"));
        this.amScanTime = Long.parseLong(props.getProperty("am.scanTime"));

        // Export Flags
        this.exportToDbQ = Boolean.parseBoolean(props.getProperty("exportToDbQ"));
        this.exportToPeQ = Boolean.parseBoolean(props.getProperty("exportToPeQ"));
        
        //Logs
        this.enableInputLogs = Boolean.parseBoolean(props.getProperty("enableInputLogs"));
        this.enableOutputLogs = Boolean.parseBoolean(props.getProperty("enableOutputLogs"));
        this.enableGeneralLogs = Boolean.parseBoolean(props.getProperty("enableGeneralLogs"));

        // Position Estimator
        this.peUrl = props.getProperty("pe.url");
        this.peToken = props.getProperty("pe.token");
        
        this.dbMaxRetries = Integer.parseInt(props.getProperty("db.maxRetries", "5"));
        this.dbRetryDelay = Integer.parseInt(props.getProperty("db.retryDelay", "10000"));
    }

    // --- Public Getters ---

    /**
     * @return The JDBC database URL.
     */
    public String getDbUrl() { return dbUrl; }

    /**
     * @return The database username.
     */
    public String getDbUsername() { return dbUsername; }

    /**
     * @return The database password.
     */
    public String getDbPassword() { return dbPassword; }

    /**
     * @return The database name.
     */
    public String getDbName() { return dbName; }

    /**
     * @return The slow scan period in milliseconds.
     */
    public long getAmSlowScanPeriod() { return amSlowScanPeriod; }

    /**
     * @return The fast scan period in milliseconds.
     */
    public long getAmFastScanPeriod() { return amFastScanPeriod; }

    /**
     * @return The scan interval in milliseconds.
     */
    public long getAmScanInterval() { return amScanInterval; }

    /**
     * @return The scan time duration in milliseconds.
     */
    public long getAmScanTime() { return amScanTime; }

    /**
     * @return {@code true} if exporting to the database queue is enabled, {@code false} otherwise.
     */
    public boolean isExportToDbQ() { return exportToDbQ; }

    /**
     * @return {@code true} if exporting to the position estimator queue is enabled, {@code false} otherwise.
     */
    public boolean isExportToPeQ() { return exportToPeQ; }

    /**
     * @return The URL for the Position Estimator service.
     */
    public String getPeUrl() { return peUrl; }

    /**
     * @return The authentication token for the Position Estimator service.
     */
    public String getPeToken() {return peToken;}
    
    public boolean isEnableInputLogs() {
		return enableInputLogs;
    }

    public boolean isEnableOutputLogs() {
		return enableOutputLogs;
    }
    
	public boolean isEnableGeneralLogs() {
		return enableGeneralLogs;
	}
	
	public int getDbMaxRetries() { return dbMaxRetries; }
	public int getDbRetryDelay() { return dbRetryDelay; }
}