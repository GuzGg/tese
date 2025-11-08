package pt.um.ucl.positioning.C03a.uwb.devices;

import java.util.ArrayList;
import java.util.List;

import pt.um.ucl.positioning.C03a.uwb.measurements.Measurement;

public class Tag extends Device {
	
	private Anchor nearestAnchor;
	private Integer distanceToNearestAnchor;
	private List<Measurement> measurements;

	public Tag(String deviceId, long initializedAt, long lastSeen) {
		super(deviceId, initializedAt, lastSeen);
		this.distanceToNearestAnchor = 0;
		this.measurements = new ArrayList<Measurement>();
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
		return this.measurements;
	}

	public void setMeasurements(List<Measurement> measurements) {
		this.measurements = measurements;
	}

}
