package uwb.measurements;

import java.util.List;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import uwb.devices.Anchor;
import uwb.devices.Tag;

public class Measurement {
	private Tag tag;
	private long measurmentId = -1;
	private List<Reading> readings;
	private long measurmentStartTime;
	private long measurmentEndTime;
	private boolean sentForOutput = false; 
	
	public Measurement (Tag tag, long measurmentStartTime, long measurmentEndTime) {
		this.tag = tag;
		this.setMeasurmentStartTime(measurmentStartTime);
		this.setMeasurmentEndTime(measurmentEndTime);
		this.readings = new ArrayList<Reading>();
	}
	
	public boolean checkIfValid(long timestamp) {
		return timestamp >= this.measurmentStartTime && timestamp <= this.measurmentEndTime;
	}
	
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

	public long getMeasurmentStartTime() {
		return measurmentStartTime;
	}

	public void setMeasurmentStartTime(long measurmentStartTime) {
		this.measurmentStartTime = measurmentStartTime;
	}

	public long getMeasurmentEndTime() {
		return measurmentEndTime;
	}

	public void setMeasurmentEndTime(long measurmentEndTime) {
		this.measurmentEndTime = measurmentEndTime;
	}
	
	public boolean getSentForOutput() {
	    return sentForOutput;
	}

	public void setSentForOutput(boolean sentForOutput) {
	    this.sentForOutput = sentForOutput;
	}
	
	public JSONObject toJson() {
		JSONObject json = new JSONObject();

		json.put("measurementID", this.getMeasurmentId());
		json.put("targetID", this.getTag().getDeviceID());
		json.put("timestamp", this.getMeasurmentEndTime());
		json.put("dataType", "ToA");
		
		JSONArray readings = new JSONArray();
		
		for(Reading reading: this.getReadings()) {
			readings.put(reading.toJson());
		}
		
		return json;
	}

	public List<Anchor> getAnchors() {
		List<Anchor> anchors = new ArrayList<Anchor>();
		this.readings.forEach(reading -> anchors.add(reading.getAnchor()));
		return  anchors;
	}
}
