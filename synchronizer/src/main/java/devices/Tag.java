package devices;

public class Tag extends Device {
	
	private Anchor nearestAnchor;
	private Integer distanceToNearestAnchor;

	public Tag(String deviceId, long initializedAt, long lastSeen) {
		super(deviceId, initializedAt, lastSeen);
		this.distanceToNearestAnchor = 0;
	}
	
	public Anchor getNearestAnchor() {
		return nearestAnchor;
	}

	public void setNearestAnchor(Anchor nearestAnchor) {
		this.nearestAnchor = nearestAnchor;
	}
	
	public Integer getDistanceToNearestAnchor() {
		return this.distanceToNearestAnchor;
	}
}
