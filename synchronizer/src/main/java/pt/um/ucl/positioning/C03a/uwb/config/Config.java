package pt.um.ucl.positioning.C03a.uwb.config;

import java.util.Properties;

/**
 * A configuration class that holds all application settings.
 * 
 * @author Gustavo Oliveira
 * @version 0.7-Reactive
 */
public class Config {

    // --- Database Properties ---
    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;
    private final String dbName;

    // --- Action Manager Properties ---
    private final long amSlowScanPeriod;
    private final long amFastScanPeriod;
    private final long amScanInterval;
    private final long amScanTime;
    private final long amMinRoundTime;
    private final long amSafetyBuffer;

    // --- Export Flags ---
    private final boolean exportToDbQ;
    private final boolean exportToPeQ;
    
    // --- Log Flags ---
    private final boolean enableInputLogs;
    private final boolean enableOutputLogs;
    private final boolean enableGeneralLogs;
   
    // --- Execution Tracking Flags ---
    private final boolean enableExecutionComparison;
    private final String logDirectory; // ---> NEW <---

    // --- Position Estimator Properties ---
    private final String peUrl;
    private final String peToken;
    
    private final int dbMaxRetries;
    private final int dbRetryDelay;
    
    private final boolean secWhitelist;

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
        this.amMinRoundTime = Long.parseLong(props.getProperty("am.minRoundTime"));
        this.amSafetyBuffer = Long.parseLong(props.getProperty("am.safetyBuffer", "50"));

        // Export Flags
        this.exportToDbQ = Boolean.parseBoolean(props.getProperty("exportToDbQ"));
        this.exportToPeQ = Boolean.parseBoolean(props.getProperty("exportToPeQ"));
        
        // Logs
        this.enableInputLogs = Boolean.parseBoolean(props.getProperty("enableInputLogs", "true"));
        this.enableOutputLogs = Boolean.parseBoolean(props.getProperty("enableOutputLogs", "true"));
        this.enableGeneralLogs = Boolean.parseBoolean(props.getProperty("enableGeneralLogs", "true"));
        
        // Execution Tracking
        this.enableExecutionComparison = Boolean.parseBoolean(props.getProperty("log.executionComparison", "false"));
        this.logDirectory = props.getProperty("log.directory", "C:/UWB_Logs"); // ---> NEW <---

        // Position Estimator
        this.peUrl = props.getProperty("pe.url");
        this.peToken = props.getProperty("pe.token");
        
        this.dbMaxRetries = Integer.parseInt(props.getProperty("db.maxRetries", "5"));
        this.dbRetryDelay = Integer.parseInt(props.getProperty("db.retryDelay", "10000"));
        
        this.secWhitelist = Boolean.parseBoolean(props.getProperty("sec.whitelist", "false"));
    }

    public String getDbUrl() { return dbUrl; }
    public String getDbUsername() { return dbUsername; }
    public String getDbPassword() { return dbPassword; }
    public String getDbName() { return dbName; }
    public long getAmSlowScanPeriod() { return amSlowScanPeriod; }
    public long getAmFastScanPeriod() { return amFastScanPeriod; }
    public long getAmScanInterval() { return amScanInterval; }
    public long getAmScanTime() { return amScanTime; }
    public boolean isExportToDbQ() { return exportToDbQ; }
    public boolean isExportToPeQ() { return exportToPeQ; }
    public String getPeUrl() { return peUrl; }
    public String getPeToken() { return peToken; }
    public boolean isEnableInputLogs() { return enableInputLogs; }
    public boolean isEnableOutputLogs() { return enableOutputLogs; }
	public boolean isEnableGeneralLogs() { return enableGeneralLogs; }
	public int getDbMaxRetries() { return dbMaxRetries; }
	public int getDbRetryDelay() { return dbRetryDelay; }
	public long getAmMinRoundTime() { return amMinRoundTime; }
	public long getAmSafetyBuffer() { return amSafetyBuffer; }
	public boolean isWhitelistEnabled() { return secWhitelist; }
	public boolean isEnableExecutionComparison() { return enableExecutionComparison; }
	public String getLogDirectory() { return logDirectory; } // ---> NEW <---
}