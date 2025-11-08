package pt.um.ucl.positioning.C03a.uwb.devices;

/**
 * 
 */
public abstract class Device {
	
	public int deviceID;
	public String deviceName;
	public long initializedAt;
	public long lastSeen;
	public boolean alive;
	
	public Device(String deviceName, long initializedAt, long lastSeen) {
		this.deviceName = deviceName;
		this.initializedAt = initializedAt;
		this.lastSeen = lastSeen;
		this.alive = true;
	}
	
	public int getDeviceID () {
		return this.deviceID;
	}
	
	public void setDeviceID(int deviceID) {
		this.deviceID = deviceID;
	}
	
	public String getDeviceName() {
		return deviceName;
	}
	
	public void setDeviceName(String deviceId) {
		this.deviceName = deviceId;
	}
	
	public long getinitializedAt() {
		return initializedAt;
	}
	
	public void setinitializedAt(long initializedAt) {
		this.initializedAt = initializedAt;
	}
	
	public long getLastSeen() {
		return lastSeen;
	}
	
	public void setLastSeen(long lastSeen) {
		this.lastSeen = lastSeen;
	}
}
