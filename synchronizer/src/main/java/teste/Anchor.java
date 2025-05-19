package teste;

import java.util.ArrayList;
import java.util.List;

public class Anchor extends Device {

	public List<Tag> listOfTags;
	
	public Anchor(byte[] deviceId, long initializedAt, long lastSeen) {
		super(deviceId, initializedAt, lastSeen);
		this.listOfTags = new ArrayList<Tag>();
	}
	
	public void addTag(Tag tag) {
		this.listOfTags.add(tag);
	}
}
