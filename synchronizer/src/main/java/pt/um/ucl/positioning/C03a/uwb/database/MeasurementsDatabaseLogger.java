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

/**
 * Handles all database logging operations for UWB measurements.
 * <p>
 * This class is responsible for saving {@link Tag}s, {@link Anchor}s,
 * {@link Measurement}s, and {@link Reading}s to a relational database.
 * It uses a {@link DataSource} for managing database connections,
 * ensuring thread-safe and efficient database access.
 * 
 * @author Gustavo Oliveira
 * @version 0.1
 */
public class MeasurementsDatabaseLogger {

    /** The base JDBC URL (e.g., "jdbc:mysql://localhost:3306"). */
    private final String dbUrlBase;
    /** The full JDBC URL including the database name (e.g., "jdbc:mysql://localhost:3306/uwb_db"). */
    private final String dbUrlWithDb;
    /** The database username. */
    private final String user;
    /** The database password. */
    private final String password;
    
    private final boolean enableLogs;

    /** The connection pool manager. */
    private final DataSource dataSource;

    /**
     * Constructs a new database logger.
     *
     * @param dataSource The {@link DataSource} to use for connection pooling.
     * @param config The {@link Config} object containing database credentials and URLs.
     */
    public MeasurementsDatabaseLogger(DataSource dataSource, Config config) {
        this.dataSource = dataSource;
        
        this.dbUrlBase = config.getDbUrl();
        this.user = config.getDbUsername();
        this.password = config.getDbPassword();
        this.enableLogs = config.isEnableOutputLogs();
        this.dbUrlWithDb = this.dbUrlBase + "/" + config.getDbName();
    }
    
    /**
     * A helper method to set a PreparedStatement parameter to {@link Types#DOUBLE}
     * or {@link Types#NULL} if the value is 0.0.
     *
     * @param stmt The PreparedStatement.
     * @param index The parameter index.
     * @param value The double value to set.
     * @throws SQLException if a database access error occurs.
     */
    private void setDoubleOrNull(PreparedStatement stmt, int index, double value) throws SQLException {
        if (value == 0.0) { 
            stmt.setNull(index, Types.DOUBLE);
        } else {
            stmt.setDouble(index, value);
        }
    }
    
    /**
     * Saves a complete measurement (Target, Measurement, and Readings) to the database.
     *
     * @param target The {@link Tag} to which the measurement pertains.
     * @param measurement The {@link Measurement} object containing all readings.
     * @return The auto-generated ID of the new measurement record, or -1 if an error occurred.
     */
    public int saveDataToA(Tag target, Measurement measurement){
        long timestamp = System.currentTimeMillis(); 
        int targetID = saveTarget(target);
        int measurementID = saveMeasurements(targetID, "ToA", measurement.getMeasurmentEndTime());

        if (targetID > 0 && measurementID > 0) {
            saveToAreadings(measurementID, timestamp, measurement.getReadings());
        }
        
        return measurementID;
    }

    /**
     * Saves a {@link Tag} (Target) to the database.
     * If the tag already exists (by targetCode), it updates the name.
     *
     * @param target The {@link Tag} to save.
     * @return The auto-generated ID of the tag, or the existing ID. Returns -1 on failure.
     */
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
        	 if(enableLogs) System.err.println("Error saving Target: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Saves a new Measurement metadata record.
     *
     * @param targetID The foreign key ID of the target.
     * @param method The measurement method (e.g., "ToA").
     * @param timestamp The timestamp of the measurement.
     * @return The auto-generated ID for this measurement, or -1 on failure.
     */
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
            if(enableLogs) System.err.println("Error saving Measurement record: " + e.getMessage());
            e.printStackTrace();
        }
        return -1; 
    }
    
    /**
     * Saves an {@link Anchor} to the database.
     * If the anchor already exists (by anchorCode), it updates its properties.
     *
     * @param anchor The {@link Anchor} to save.
     * @return The auto-generated ID of the anchor, or the existing ID. Returns -1 on failure.
     */
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
            
            // Assuming default position 0,0,0 if not set
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
        	 if(enableLogs) System.err.println("Error saving Anchor: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Saves a list of Time-of-Arrival (ToA) readings in a batch operation.
     *
     * @param measurementId The foreign key ID of the parent measurement.
     * @param timestamp A general timestamp for the batch (note: individual readings also have timestamps).
     * @param readings The list of {@link Reading} objects to save.
     */
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
                stmt.setLong(2, reading.getTimestamp()); // Use the individual reading's timestamp
                stmt.setInt(3, anchor.getDeviceID());
                stmt.setDouble(4, reading.getDisctance()); 
                
                stmt.addBatch();
        	}

            stmt.executeBatch();
        } catch (SQLException e) {
        	 if(enableLogs) System.err.println("Error saving ToA readings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the database ID for a Target given its unique code (device name).
     *
     * @param targetCode The unique string code of the target.
     * @return The integer ID, or -1 if not found or an error occurs.
     */
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
        	 if(enableLogs) System.err.println("DB Error for tag " + targetCode + ": " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Retrieves the database ID for an Anchor given its unique code (device name).
     *
     * @param anchorCode The unique string code of the anchor.
     * @return The integer ID, or -1 if not found or an error occurs.
     */
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
        	 if(enableLogs) System.err.println("DB Error for anchor " + anchorCode + ": " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }
    
    /**
     * Deletes all records from a specified table.
     *
     * @param tableName The name of the table to clear.
     * @throws SQLException if a database access error occurs or the table name is invalid.
     */
    public void clearTable(String tableName) throws SQLException {
        String query = "DELETE FROM " + tableName;

        try (Connection conn = dataSource.getConnection(); // Correctly uses DataSource
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }
}