package measurements;

import java.util.List;

import devices.Tag;

public class Measurement {
	private Tag tag;
	private long measurmentId;
	private List<Reading> readings;
	
	public long getMeasurmentId() {
		return measurmentId;
	}
	public void setMeasurmentId(long measurmentId) {
		this.measurmentId = measurmentId;
	}
	public Tag getTag() {
		return tag;
	}
	public void setTag(Tag tag) {
		this.tag = tag;
	}
	public List<Reading> getReadings() {
		return readings;
	}
	public void setReadings(List<Reading> readings) {
		this.readings = readings;
	}
}
