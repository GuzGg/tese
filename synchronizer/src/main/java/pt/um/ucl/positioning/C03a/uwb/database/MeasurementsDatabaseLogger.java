package pt.um.ucl.positioning.C03a.uwb.database;

import pt.um.ucl.positioning.C03a.uwb.config.Config;
import pt.um.ucl.positioning.C03a.uwb.devices.Anchor;
import pt.um.ucl.positioning.C03a.uwb.devices.Tag;
import pt.um.ucl.positioning.C03a.uwb.measurements.Measurement;
import pt.um.ucl.positioning.C03a.uwb.measurements.Reading;

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

    // Removed redundant fields (DB_URL, DB_NAME)
    private final String dbUrlBase;
    private final String dbUrlWithDb; // New field for clarity in initializeDatabase
    private final String user;
    private final String password;

    private final DataSource dataSource;

    /**
     * Constructor accepts a DataSource (for connection pooling) for thread-safe access.
     */
    public MeasurementsDatabaseLogger(DataSource dataSource, Config config) {
        this.dataSource = dataSource;
        
        // These fields are needed only for the special case of database creation (initializeDatabase).
        this.dbUrlBase = config.getDbUrl();
        this.user = config.getDbUsername();
        this.password = config.getDbPassword();
        
        // Build the full URL here for the table creation step
        this.dbUrlWithDb = this.dbUrlBase + "/" + config.getDbName();
    }
    
    // Helper methods (setDoubleOrNull remains the same)
    private void setDoubleOrNull(PreparedStatement stmt, int index, double value) throws SQLException {
        if (value == 0.0) { 
            stmt.setNull(index, Types.DOUBLE);
        } else {
            stmt.setDouble(index, value);
        }
    }

    // --- Data Access Methods (Synchronization REMOVED) ---
    // The DataSource ensures thread safety via connection pooling. Synchronization here harms throughput.

    public int saveDataToA(Tag target, Measurement measurement){
        long timestamp = System.currentTimeMillis(); 
        int targetID = saveTarget(target);
        int measurementID = saveMeasurements(targetID, "ToA", measurement.getMeasurmentEndTime());

        if (targetID > 0 && measurementID > 0) {
            saveToAreadings(measurementID, timestamp, measurement.getReadings());
        }
        
        return measurementID;
    }

    public int saveTarget(Tag target){
        final String sql = """
            INSERT INTO Targets (targetCode, targetName) 
            VALUES (?, ?) 
            ON DUPLICATE KEY UPDATE targetName = VALUES(targetName)
            """;
        
        try (Connection conn = dataSource.getConnection(); // Correctly uses DataSource
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
        
        try (Connection conn = dataSource.getConnection(); // Correctly uses DataSource
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
    
    public int saveAnchor(Anchor anchor) {
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
        
        try (Connection conn = dataSource.getConnection(); // Correctly uses DataSource
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

    public void saveToAreadings(int measurementId, long timestamp, List<Reading> readings) {    	
        final String sql = """
            INSERT INTO ToAreadings (measurementID, timestamp, anchorID, `Range`) 
            VALUES (?, ?, ?, ?) 
            """;
        
        try (Connection conn = dataSource.getConnection(); // Correctly uses DataSource
             PreparedStatement stmt = conn.prepareStatement(sql)) {
        	
        	for (Reading reading: readings) {
        		Anchor anchor = reading.getAnchor();
                stmt.setInt(1, measurementId);
                stmt.setLong(2, reading.getTimestamp()); 
                stmt.setInt(3, anchor.getDeviceID());
                stmt.setDouble(4, reading.getDisctance()); 
                
                stmt.addBatch();
        	}

            stmt.executeBatch();
        } catch (SQLException e) {
            System.err.println("Error saving ToA readings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int getTargetIdByCode(String targetCode) {
        final String sql = "SELECT targetID FROM Targets WHERE targetCode = ?";
        try (Connection conn = dataSource.getConnection(); // Correctly uses DataSource
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
        try (Connection conn = dataSource.getConnection(); // Correctly uses DataSource
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

        try (Connection conn = dataSource.getConnection(); // Correctly uses DataSource
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }
}