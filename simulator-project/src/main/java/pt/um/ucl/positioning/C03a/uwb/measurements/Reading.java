package pt.um.ucl.positioning.C03a.uwb.measurements;

import org.json.JSONObject;

import pt.um.ucl.positioning.C03a.uwb.devices.Anchor;

/**
 * Represents a single distance reading from one {@link Anchor} to one {@link Tag}.
 * <p>
 * This is the most granular piece of measurement data, typically aggregated
 * into a {@link Measurement} object.
 * 
 * @author Gustavo Oliveira
 * @version 0.2
 */
public class Reading {
	/** The anchor that took this reading. */
	private Anchor anchor;
	/** The measured distance (e.g., in millimeters). */
	private double distance;
	/** The timestamp when this specific reading was taken. */
	private long timestamp;
	/** The UWB channel used for this reading. */
	private int channel;
	
	/**
	 * Constructs a new Reading.
	 *
	 * @param anchor The {@link Anchor} that performed the reading.
	 * @param distance The measured distance.
	 * @param timestamp The timestamp of the reading.
	 * @param channel The UWB channel used.
	 */
	public Reading (Anchor anchor, double distance, long timestamp, int channel) {
		this.anchor = anchor;
		this.distance = distance;
		this.timestamp = timestamp;
		this.channel = channel;
	}
	
	/**
	 * Gets the anchor that took this reading.
	 * @return The {@link Anchor}.
	 */
	public Anchor getAnchor() {
		return anchor;
	}

	/**
	 * Sets the anchor for this reading.
	 * @param anchor The new {@link Anchor}.
	 */
	public void setAnchor(Anchor anchor) {
		this.anchor = anchor;
	}

	/**
	 * Gets the measured distance.
	 * @return The distance.
	 */
	public double getDisctance() {
		return distance;
	}

	/**
	 * Sets the measured distance.
	 * @param disctance The new distance.
	 */
	public void setDisctance(double distance) {
		this.distance = distance;
	}

	/**
	 * Gets the timestamp of this reading.
	 * @return The timestamp.
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * Sets the timestamp for this reading.
	 * @param timestamp The new timestamp.
	 */
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Gets the UWB channel used for this reading.
	 * @return The channel number.
	 */
	public int getChannel() {
		return channel;
	}

	/**
	 * Sets the UWB channel for this reading.
	 * @param channel The new channel number.
	 */
	public void setChannel(int channel) {
		this.channel = channel;
	}
	
	/**
	 * Converts this Reading object into a JSONObject format.
	 *
	 * @return A {@link JSONObject} representation of the reading.
	 */
	public JSONObject toJson() {
		JSONObject json = new JSONObject();
		
		json.put("anchorID", this.getAnchor().getDeviceID());
		json.put("timestamp", this.getTimestamp());
		json.put("distance", this.getDisctance());
		json.put("channel", this.getChannel());
		
		return json;
	}
}