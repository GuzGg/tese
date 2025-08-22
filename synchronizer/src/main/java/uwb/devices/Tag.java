package uwb.devices;

import java.util.List;

import uwb.measurements.Measurement;

public class Tag extends Device {
	
	private Anchor nearestAnchor;
	private Integer distanceToNearestAnchor;
	private List<Measurement> measurements;

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

	public List<Measurement> getMeasurements() {
		return measurements;
	}

	public void setMeasurements(List<Measurement> measurements) {
		this.measurements = measurements;
	}

}
