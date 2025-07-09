package devices;

/**
 * 
 */
public abstract class Device {
	
	public String deviceId;
	public long initializedAt;
	public long lastSeen;
	public boolean alive;
	
	public Device(String deviceId, long initializedAt, long lastSeen) {
		super();
		this.deviceId = deviceId;
		this.initializedAt = initializedAt;
		this.lastSeen = lastSeen;
		this.alive = true;
	}
	
	public String getDeviceId() {
		return deviceId;
	}
	
	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
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
