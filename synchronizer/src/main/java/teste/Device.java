package teste;

/**
 * 
 */
public abstract class Device {
	
	public byte[] deviceId;
	public long initializedAt;
	public long lastSeen;
	public boolean alive;
	
	public Device(byte[] deviceId, long initializedAt, long lastSeen) {
		super();
		this.deviceId = deviceId;
		this.initializedAt = initializedAt;
		this.lastSeen = lastSeen;
		this.alive = true;
	}
	
	public byte[] getDeviceId() {
		return deviceId;
	}
	public void setDeviceId(byte[] deviceId) {
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
