package uwb.config;

import java.util.Properties;

public class Config {

    // Database
    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;
    private final String dbName;

    // Action Manager
    private final long amSlowScanPeriod;
    private final long amFastScanPeriod;
    private final long amScanInterval;
    private final long amScanTime;

    // Export Flags
    private final boolean exportToDbQ;
    private final boolean exportToPeQ;

    // Position Estimator
    private final String peUrl;
    private final String peToken;

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

        // Position Estimator
        this.peUrl = props.getProperty("pe.url");
        this.peToken = props.getProperty("pe.token");
    }

    // Public getters for all properties
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
    public String getPeToken() {return peToken;}
}