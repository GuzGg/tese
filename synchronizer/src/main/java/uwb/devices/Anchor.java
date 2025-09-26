package uwb.devices;

import java.util.ArrayList;
import java.util.List;

public class Anchor extends Device {

	public List<Tag> listOfTags;
	public int range = 20;
	
	public Anchor(String deviceId, long initializedAt, long lastSeen) {
		super(deviceId, initializedAt, lastSeen);
		this.listOfTags = new ArrayList<Tag>();
	}
	
	public void addTag(Tag tag) {
		this.listOfTags.add(tag);
	}
	
	public int getRange() {
		return this.range;
	}
}
