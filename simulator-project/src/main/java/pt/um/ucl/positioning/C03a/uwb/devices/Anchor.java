package pt.um.ucl.positioning.C03a.uwb.devices;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a UWB Anchor device.
 * <p>
 * Anchors are typically stationary devices that detect and measure
 * distances to {@link Tag} devices. This class extends the base
 * {@link Device} class.
 * 
 * @author Gustavo Oliveira
 * @version 0.4
 */
public class Anchor extends Device {

	/** A list of tags that this anchor has detected or is aware of. */
	public List<Tag> listOfTags;
	/** The maximum effective range of the anchor (e.g., in meters). */
	public int range = 20;
	
	/**
	 * Constructs a new Anchor device.
	 *
	 * @param deviceId The unique name or identifier for this anchor.
	 * @param initializedAt The timestamp when the anchor was first initialized.
	 * @param lastSeen The timestamp when the anchor was last seen or active.
	 */
	public Anchor(String deviceName, long initializedAt, long lastSeen) {
		super(deviceName, initializedAt, lastSeen);
		this.listOfTags = new ArrayList<Tag>();
	}
	
	/**
	 * Adds a tag to this anchor's list of known tags.
	 *
	 * @param tag The {@link Tag} to add.
	 */
	public void addTag(Tag tag) {
		this.listOfTags.add(tag);
	}
	
	/**
	 * Gets the effective range of this anchor.
	 *
	 * @return The range value.
	 */
	public int getRange() {
		return this.range;
	}
}