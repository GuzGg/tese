package pt.um.ucl.positioning.C03a.uwb.devices;

import java.util.ArrayList;
import java.util.List;

import pt.um.ucl.positioning.C03a.uwb.measurements.Measurement;

/**
 * Represents a UWB Tag device.
 * <p>
 * Tags are typically mobile devices whose positions are tracked by
 * stationary {@link Anchor} devices. This class extends the base
 * {@link Device} class and holds a list of its associated
 * {@link Measurement}s.
 * 
 * @author Gustavo Oliveira
 * @version 0.1
 */
public class Tag extends Device {
	
	/** The anchor currently closest to this tag. */
	private Anchor nearestAnchor;
	/** The measured distance to the nearest anchor. */
	private Integer distanceToNearestAnchor;
	/** A list of all measurement rounds this tag has been a part of. */
	private List<Measurement> measurements;

	/**
	 * Constructs a new Tag device.
	 *
	 * @param deviceId The unique name or identifier for this tag.
	 * @param initializedAt The timestamp when the tag was first initialized.
	 * @param lastSeen The timestamp when the tag was last seen or active.
	 */
	public Tag(String deviceId, long initializedAt, long lastSeen) {
		super(deviceId, initializedAt, lastSeen);
		this.distanceToNearestAnchor = 0;
		this.measurements = new ArrayList<Measurement>();
	}
	
	/**
	 * Gets the anchor currently considered nearest to this tag.
	 *
	 * @return The nearest {@link Anchor}, or {@code null} if not set.
	 */
	public Anchor getNearestAnchor() {
		return nearestAnchor;
	}

	/**
	 * Sets the nearest anchor for this tag.
	 *
	 * @param nearestAnchor The {@link Anchor} to set as nearest.
	 */
	public void setNearestAnchor(Anchor nearestAnchor) {
		this.nearestAnchor = nearestAnchor;
	}
	
	/**
	 * Gets the last recorded distance to the nearest anchor.
	 *
	 * @return The distance.
	 */
	public Integer getDistanceToNearestAnchor() {
		return this.distanceToNearestAnchor;
	}

	/**
	 * Gets the list of measurements associated with this tag.
	 *
	 * @return A list of {@link Measurement} objects.
	 */
	public List<Measurement> getMeasurements() {
		return this.measurements;
	}

	/**
	 * Sets or replaces the list of measurements for this tag.
	 *
	 * @param measurements The new list of {@link Measurement} objects.
	 */
	public void setMeasurements(List<Measurement> measurements) {
		this.measurements = measurements;
	}

}