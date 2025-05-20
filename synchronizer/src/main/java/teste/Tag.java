package teste;

public class Tag extends Device {
	
	public Anchor nearestAnchor;

	public Tag(byte[] deviceId, long initializedAt, long lastSeen) {
		super(deviceId, initializedAt, lastSeen);
	}
	
	public Anchor getNearestAnchor() {
		return nearestAnchor;
	}

	public void setNearestAnchor(Anchor nearestAnchor) {
		this.nearestAnchor = nearestAnchor;
	}
}
