package teste;

public class Tag extends Device {
	
	public byte[] nearestAnchor;

	public Tag(byte[] deviceId, long initializedAt, long lastSeen) {
		super(deviceId, initializedAt, lastSeen);
	}
	
	public byte[] getNearestAnchor() {
		return nearestAnchor;
	}

	public void setNearestAnchor(byte[] nearestAnchor) {
		this.nearestAnchor = nearestAnchor;
	}
}
