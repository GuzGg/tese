package pt.um.ucl.positioning.C03a.uwb.devices;

/**
 * An abstract base class for a UWB device, such as an Anchor or a Tag.
 * <p>
 * This class contains common properties like device ID, name, and activity timestamps.
 * 
 * @author Gustavo Oliveira
 * @version 0.1
 */
public abstract class Device {
	
	/** The numerical device ID. */
	public int deviceID;
	/** The string-based device name or identifier. */
	public String deviceName;
	/** The timestamp (epoch milliseconds) when the device was initialized. */
	public long initializedAt;
	/** The timestamp (epoch milliseconds) when the device was last seen. */
	public long lastSeen;
	/** A flag indicating if the device is currently considered active or "alive". */
	public boolean alive;
	
	/**
	 * Constructs a new Device.
	 *
	 * @param deviceName The unique name or identifier for this device.
	 * @param initializedAt The timestamp when the device was initialized.
	 * @param lastSeen The timestamp when the device was last seen.
	 */
	public Device(String deviceName, long initializedAt, long lastSeen) {
		this.deviceName = deviceName;
		this.initializedAt = initializedAt;
		this.lastSeen = lastSeen;
		this.alive = true;
	}
	
	/**
	 * Gets the numerical device ID.
	 *
	 * @return The device ID.
	 */
	public int getDeviceID () {
		return this.deviceID;
	}
	
	/**
	 * Sets the numerical device ID.
	 *
	 * @param deviceID The new device ID.
	 */
	public void setDeviceID(int deviceID) {
		this.deviceID = deviceID;
	}
	
	/**
	 * Gets the string-based device name.
	 *
	 * @return The device name.
	 */
	public String getDeviceName() {
		return deviceName;
	}
	
	/**
	 * Sets the string-based device name.
	 *
	 * @param deviceId The new device name.
	 */
	public void setDeviceName(String deviceId) {
		this.deviceName = deviceId;
	}
	
	/**
	 * Gets the initialization timestamp.
	 *
	 * @return The initialization timestamp (epoch milliseconds).
	 */
	public long getinitializedAt() {
		return initializedAt;
	}
	
	/**
	 * Sets the initialization timestamp.
	 *
	 * @param initializedAt The new initialization timestamp (epoch milliseconds).
	 */
	public void setinitializedAt(long initializedAt) {
		this.initializedAt = initializedAt;
	}
	
	/**
	 * Gets the last seen timestamp.
	 *
	 * @return The last seen timestamp (epoch milliseconds).
	 */
	public long getLastSeen() {
		return lastSeen;
	}
	
	/**
	 * Sets the last seen timestamp.
	 *
	 * @param lastSeen The new last seen timestamp (epoch milliseconds).
	 */
	public void setLastSeen(long lastSeen) {
		this.lastSeen = lastSeen;
	}
}