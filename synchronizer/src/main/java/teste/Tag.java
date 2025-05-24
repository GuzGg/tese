package teste;

public class Tag extends Device {
	
	public Anchor nearestAnchor;
	public Integer distance;

	public Tag(String deviceId, long initializedAt, long lastSeen) {
		super(deviceId, initializedAt, lastSeen);
		this.distance = 0;
	}
	
	public Anchor getNearestAnchor() {
		return nearestAnchor;
	}

	public void setNearestAnchor(Anchor nearestAnchor) {
		this.nearestAnchor = nearestAnchor;
	}
}
