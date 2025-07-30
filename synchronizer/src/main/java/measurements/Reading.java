package measurements;

import devices.Anchor;

public class Reading {
	private Anchor anchor;
	private long disctance;
	private long timestamp;
	private int channel;
	
	public Anchor getAnchor() {
		return anchor;
	}
	public void setAnchor(Anchor anchor) {
		this.anchor = anchor;
	}
	public long getDisctance() {
		return disctance;
	}
	public void setDisctance(long disctance) {
		this.disctance = disctance;
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
