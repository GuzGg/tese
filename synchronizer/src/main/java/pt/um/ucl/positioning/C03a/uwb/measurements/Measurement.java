package pt.um.ucl.positioning.C03a.uwb.measurements;

import java.util.List;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import pt.um.ucl.positioning.C03a.uwb.devices.Anchor;
import pt.um.ucl.positioning.C03a.uwb.devices.Tag;

/**
 * Represents a single measurement cycle for a specific {@link Tag}.
 * <p>
 * A Measurement aggregates multiple {@link Reading} objects, each from a
 * different {@link Anchor}, all pertaining to the same tag within a
 * defined time window (start and end time).
 * 
 * @author Gustavo Oliveira
 * @version 0.3
 */
public class Measurement {
	/** The tag being measured. */
	private Tag tag;
	/** The unique identifier for this measurement. */
	private long measurmentId = -1;
	/** A list of individual distance readings from various anchors. */
	private List<Reading> readings;
	/** The timestamp marking the start of this measurement's time window. */
	private long measurmentStartTime;
	/** The timestamp marking the end of this measurement's time window. */
	private long measurmentEndTime;
	/** Flag to indicate if this measurement has been sent for output processing. */
	private boolean sentForOutput = false; 
	
	/**
	 * Constructs a new Measurement.
	 *
	 * @param tag The {@link Tag} that is the subject of this measurement.
	 * @param measurmentStartTime The start time of the valid window for this measurement.
	 * @param measurmentEndTime The end time of the valid window for this measurement.
	 */
	public Measurement (Tag tag, long measurmentStartTime, long measurmentEndTime) {
		this.tag = tag;
		this.setMeasurmentStartTime(measurmentStartTime);
		this.setMeasurmentEndTime(measurmentEndTime);
		this.readings = new ArrayList<Reading>();
	}
	
	/**
	 * Checks if a given timestamp falls within this measurement's valid time window.
	 *
	 * @param timestamp The timestamp to check.
	 * @return {@code true} if the timestamp is between the start and end time (inclusive),
	 * {@code false} otherwise.
	 */
	public boolean checkIfValid(long timestamp) {
		return timestamp >= this.measurmentStartTime && timestamp <= this.measurmentEndTime;
	}
	
	/**
	 * Gets the measurement ID.
	 * @return The measurement ID.
	 */
	public long getMeasurmentId() {
		return measurmentId;
	}

	/**
	 * Sets the measurement ID.
	 * @param measurmentId The new measurement ID.
	 */
	public void setMeasurmentId(long measurmentId) {
		this.measurmentId = measurmentId;
	}

	/**
	 * Gets the tag associated with this measurement.
	 * @return The {@link Tag}.
	 */
	public Tag getTag() {
		return tag;
	}

	/**
	 * Sets the tag for this measurement.
	 * @param tag The new {@link Tag}.
	 */
	public void setTag(Tag tag) {
		this.tag = tag;
	}

	/**
	 * Gets the list of readings for this measurement.
	 * @return A list of {@link Reading} objects.
	 */
	public List<Reading> getReadings() {
		return readings;
	}

	/**
	 * Sets the list of readings for this measurement.
	 * @param readings A new list of {@link Reading} objects.
	 */
	public void setReadings(List<Reading> readings) {
		this.readings = readings;
	}

	/**
	 * Gets the start time of the measurement window.
	 * @return The start timestamp.
	 */
	public long getMeasurmentStartTime() {
		return measurmentStartTime;
	}

	/**
	 * Sets the start time of the measurement window.
	 * @param measurmentStartTime The new start timestamp.
	 */
	public void setMeasurmentStartTime(long measurmentStartTime) {
		this.measurmentStartTime = measurmentStartTime;
	}

	/**
	 * Gets the end time of the measurement window.
	 * @return The end timestamp.
	 */
	public long getMeasurmentEndTime() {
		return measurmentEndTime;
	}

	/**
	 * Sets the end time of the measurement window.
	 * @param measurmentEndTime The new end timestamp.
	 */
	public void setMeasurmentEndTime(long measurmentEndTime) {
		this.measurmentEndTime = measurmentEndTime;
	}
	
	/**
	 * Checks if this measurement has been marked as sent for output.
	 * @return {@code true} if sent, {@code false} otherwise.
	 */
	public boolean getSentForOutput() {
	    return sentForOutput;
	}

	/**
	 * Marks this measurement as sent (or not sent) for output.
	 * @param sentForOutput The new boolean status.
	 */
	public void setSentForOutput(boolean sentForOutput) {
	    this.sentForOutput = sentForOutput;
	}
	
	/**
	 * Converts this Measurement object into a JSONObject format.
	 * <p>
	 * The JSON includes the measurement ID, target tag ID, timestamp (end time),
	 * data type, and an array of all associated readings.
	 *
	 * @return A {@link JSONObject} representation of the measurement.
	 */
	public JSONObject toJson() {
		JSONObject json = new JSONObject();

		json.put("measurementID", this.getMeasurmentId());
		json.put("targetID", this.getTag().getDeviceID());
		json.put("targetCode", this.getTag().getDeviceName());
		json.put("timestamp", this.getMeasurmentEndTime());
		json.put("dataType", "ToA"); // Time of Arrival
		
		JSONArray readings = new JSONArray();
		
		for(Reading reading: this.getReadings()) {
			readings.put(reading.toJson());
		}
		
		json.put("readings", readings);
		
		return json;
	}

	/**
	 * Retrieves a list of all unique anchors involved in this measurement.
	 *
	 * @return A list of {@link Anchor} objects that provided readings.
	 */
	public List<Anchor> getAnchors() {
		List<Anchor> anchors = new ArrayList<Anchor>();
		this.readings.forEach(reading -> anchors.add(reading.getAnchor()));
		return  anchors;
	}
}