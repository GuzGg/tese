package measurements;

import devices.Anchor;

public class Reading {
	private Anchor anchor;
	private long distance;
	private long timestamp;
	private int channel;
	
	public Reading (Anchor anchor, long distance, long timestamp, int channel) {
		this.anchor = anchor;
		this.distance = distance;
		this.timestamp = timestamp;
		this.channel = channel;
	}
	
	public Anchor getAnchor() {
		return anchor;
	}
	public void setAnchor(Anchor anchor) {
		this.anchor = anchor;
	}
	public long getDisctance() {
		return distance;
	}
	public void setDisctance(long disctance) {
		this.distance = disctance;
	}
	public long getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	public int getChannel() {
		return channel;
	}
	public void setChannel(int channel) {
		this.channel = channel;
	}
}
