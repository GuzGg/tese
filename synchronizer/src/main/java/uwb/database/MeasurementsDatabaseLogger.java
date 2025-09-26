package uwb.database;

import uwb.config.Config;
import uwb.devices.Anchor;
import uwb.devices.Tag;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

import javax.sql.DataSource; 

public class MeasurementsDatabaseLogger {

    private final String DB_URL_BASE;
    private final String DB_NAME;
    private final String DB_URL;
    private final String USER;
    private final String PASSWORD;

    private final DataSource dataSource; 

    /**
     * Constructor accepts a DataSource (for connection pooling) for thread-safe access.
     */
    public MeasurementsDatabaseLogger(DataSource dataSource, Config config) {
        this.dataSource = dataSource; 
        this.DB_URL_BASE = config.getDbUrl();
        this.DB_NAME = config.getDbName();
        this.USER = config.getDbUsername();
        this.PASSWORD = config.getDbPassword();
        this.DB_URL = this.DB_URL_BASE + "/" + this.DB_NAME;
    }
    
    private void setDoubleOrNull(PreparedStatement stmt, int index, double value) throws SQLException {
        if (value == 0.0) { 
            stmt.setNull(index, Types.DOUBLE);
        } else {
            stmt.setDouble(index, value);
        }
    }

    /**
     * Initializes the database and tables if they do not exist. (Uses DriverManager for setup only)
     */
    public synchronized void initializeDatabase() {
        try {
            // Create database if it doesn't exist (using DB_URL_BASE)
            try (Connection conn = DriverManager.getConnection(this.DB_URL_BASE, this.USER, this.PASSWORD);
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + this.DB_NAME);
                System.out.println("Database ensured: " + this.DB_NAME);
            }

            // Create tables if they don't exist (using full DB_URL)
            try (Connection conn = DriverManager.getConnection(this.DB_URL, this.USER, this.PASSWORD);
                 Statement stmt = conn.createStatement()) {

                // Targets Table
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS Targets (
                    targetID INT AUTO_INCREMENT PRIMARY KEY,
                    targetCode VARCHAR(50) UNIQUE,
                    targetName VARCHAR(50)
                    );
                """);
                
                // Measurements Table
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS Measurements (
                    measurementID INT AUTO_INCREMENT PRIMARY KEY,
                    targetID INT,
                    timestamp BIGINT,
                    dataType VARCHAR(20),
                    FOREIGN KEY (targetID) REFERENCES Targets(targetID)
                    );
                """);
                
                // Anchors Table
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS Anchors (
                    anchorID INT AUTO_INCREMENT PRIMARY KEY,
                    anchorCode VARCHAR(50) UNIQUE,
                    anchorName VARCHAR(50),
                    anchorX DOUBLE,
                    anchorY DOUBLE,
                    anchorZ DOUBLE,
                    anchorAlpha DOUBLE,
                    anchorBeta DOUBLE,
                    anchorGamma DOUBLE
                    );
                """);
                
                // AoAreadings Table
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS AoAreadings (
                    readingID INT AUTO_INCREMENT PRIMARY KEY,
                    measurementID INT,
                    timestamp BIGINT,
                    anchorID INT,
                    Elevation DOUBLE,
                    Azimuth DOUBLE,
                    FOREIGN KEY (measurementID) REFERENCES Measurements(measurementID),
                    FOREIGN KEY (anchorID) REFERENCES Anchors(anchorID)
                    );
                """);
                
                // ToAreadings Table
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS ToAreadings (
                    readingID INT AUTO_INCREMENT PRIMARY KEY,
                    measurementID INT,
                    timestamp BIGINT,
                    anchorID INT,
                    tof DOUBLE,
                    `Range` DOUBLE,
                    FOREIGN KEY (measurementID) REFERENCES Measurements(measurementID),
                    FOREIGN KEY (anchorID) REFERENCES Anchors(anchorID)
                    );
                """);
                
                // RSSIreadings Table
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS RSSIreadings (
                    readingID INT AUTO_INCREMENT PRIMARY KEY,
                    measurementID INT,
                    timestamp BIGINT,
                    anchorID INT,
                    Rssi DOUBLE,
                    FOREIGN KEY (measurementID) REFERENCES Measurements(measurementID),
                    FOREIGN KEY (anchorID) REFERENCES Anchors(anchorID)
                    );
                """);
                
                System.out.println("Tables ensured.");
            }
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void saveDataToA(List<Anchor> anchors, Tag target){
        long timestamp = System.currentTimeMillis(); 
        int targetID = saveTarget(target);
        int measurementID = saveMeasurements(targetID,"ToA", timestamp);

        if (targetID > 0 && measurementID > 0) {
            List<Integer> anchorIDs = saveAnchorsAndGetIDs(anchors);
            
            if (!anchorIDs.isEmpty()) {
                batchSaveToAreadings(measurementID, timestamp, anchors, anchorIDs);
            }
        }
    }

    public synchronized int saveTarget(Tag target){
        final String sql = """
            INSERT INTO Targets (targetCode, targetName) 
            VALUES (?, ?) 
            ON DUPLICATE KEY UPDATE targetName = VALUES(targetName)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
             
            stmt.setString(1, target.getDeviceName());
            stmt.setString(2, target.getDeviceName()); 
            
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            
            return getTargetIdByCode(target.getDeviceName()); 

        } catch (SQLException e) {
            System.err.println("Error saving Target: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public int saveMeasurements(int targetID, String method, long timestamp){
        final String sql = """
            INSERT INTO Measurements (targetID, timestamp, dataType) 
            VALUES (?, ?, ?)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, targetID); 
            stmt.setLong(2, timestamp);
            stmt.setString(3, method);
            
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1); 
                }
            }
        } catch (SQLException e) {
            System.err.println("Error saving Measurement record: " + e.getMessage());
            e.printStackTrace();
        }
        return -1; 
    }
    
    public synchronized int saveAnchor(Anchor anchor) {
        final String sql = """
            INSERT INTO Anchors (anchorCode, anchorName, anchorX, anchorY, anchorZ, anchorAlpha, anchorBeta, anchorGamma) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE 
                anchorName = VALUES(anchorName),
                anchorX = VALUES(anchorX),
                anchorY = VALUES(anchorY),
                anchorZ = VALUES(anchorZ),
                anchorAlpha = VALUES(anchorAlpha), 
                anchorBeta = VALUES(anchorBeta), 
                anchorGamma = VALUES(anchorGamma)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, anchor.getDeviceName());
            stmt.setString(2, anchor.getDeviceName()); 
            
            setDoubleOrNull(stmt, 3, 0); 
            setDoubleOrNull(stmt, 4, 0);
            setDoubleOrNull(stmt, 5, 0);
            setDoubleOrNull(stmt, 6, 0);
            setDoubleOrNull(stmt, 7, 0);
            setDoubleOrNull(stmt, 8, 0);
            
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            
            return getAnchorIdByCode(anchor.getDeviceName());
            
        } catch (SQLException e) {
            System.err.println("Error saving Anchor: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public List<Integer> saveAnchorsAndGetIDs(List<? extends Anchor> anchors){
        final String sql = """
            INSERT INTO Anchors (anchorCode, anchorName, anchorX, anchorY, anchorZ, anchorAlpha, anchorBeta, anchorGamma) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE 
                anchorName = VALUES(anchorName),
                anchorX = VALUES(anchorX),
                anchorY = VALUES(anchorY),
                anchorZ = VALUES(anchorZ),
                anchorAlpha = VALUES(anchorAlpha), 
                anchorBeta = VALUES(anchorBeta), 
                anchorGamma = VALUES(anchorGamma)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            for (Anchor anchor : anchors) {
                stmt.setString(1, anchor.getDeviceName());
                stmt.setString(2, anchor.getDeviceName()); 
                
                setDoubleOrNull(stmt, 3, 0);
                setDoubleOrNull(stmt, 4, 0);
                setDoubleOrNull(stmt, 5, 0);
                setDoubleOrNull(stmt, 6, 0);
                setDoubleOrNull(stmt, 7, 0);
                setDoubleOrNull(stmt, 8, 0);
                stmt.addBatch();
            }

            stmt.executeBatch();
            
            return anchors.stream()
                         .map(a -> getAnchorIdByCode(a.getDeviceName()))
                         .toList();

        } catch (SQLException e) {
            System.err.println("Error saving Anchors: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }

    public void batchSaveToAreadings(int measurementId, long timestamp, List<Anchor> anchors, List<Integer> anchorIDs) {
        final String sql = """
            INSERT INTO ToAreadings (measurementID, timestamp, anchorID, `Range`) 
            VALUES (?, ?, ?, ?) 
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < anchors.size(); i++) {
                Anchor anchor = anchors.get(i);
                int anchorID = anchorIDs.get(i);
                
                double range = anchor.getRange(); 
                
                stmt.setInt(1, measurementId);
                stmt.setLong(2, timestamp); 
                stmt.setInt(3, anchorID);
                stmt.setDouble(4, range); 
                
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            System.err.println("Error batch saving ToA readings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int getTargetIdByCode(String targetCode) {
        final String sql = "SELECT targetID FROM Targets WHERE targetCode = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("targetID");
                }
            }
        } catch (SQLException e) {
            System.err.println("DB Error for tag " + targetCode + ": " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    public int getAnchorIdByCode(String anchorCode) {
        final String sql = "SELECT anchorID FROM Anchors WHERE anchorCode = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, anchorCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("anchorID");
                }
            }
        } catch (SQLException e) {
            System.err.println("DB Error for anchor " + anchorCode + ": " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }
    
    public void clearTable(String tableName) throws SQLException {
        String query = "DELETE FROM " + tableName;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }
}