package teste;

import java.util.HashMap;
import java.util.Map;

public class Synchronizer {
	
	public Map<String, Tag> listOfTags;
	public  Map<String, Anchor> listOfAnchors;
	// tagId -> {anchorId -> distance}
	public Map<String, Map<String, Double>>  distances;
	
	public Synchronizer(Map<String, Tag> listOfTags, Map<String, Anchor> listOfAnchors) {
		super();
		this.listOfTags = listOfTags;
		this.listOfAnchors = listOfAnchors;
	}
	
	public Synchronizer() {
		this.listOfTags = new HashMap<String, Tag>();
		this.listOfAnchors = new HashMap<String, Anchor>();
	}
	
	/**
	 * Add new anchor to listOfAnchors
	 * @param anchor anchor to be added to the map
	 */
	public void addNewAnchor(Anchor anchor) {
		this.listOfAnchors.put(anchor.getDeviceId(), anchor);
	}
	
	/**
	 * Check if anchor exists
	 * @param anchor anchor to be checked
	 * @return if an anchor exists or not
	 */
	public boolean anchorExists(Anchor anchor) {
		return this.listOfAnchors.containsKey(anchor.getDeviceId());
	}
	
	/**
	 * Add new Tag to listOfTags
	 * @param tag tag to be added
	 */
	public void addNewTag(Tag tag) {
		this.listOfTags.put(tag.getDeviceId(), tag);
		this.listOfAnchors.values().forEach(anchor -> anchor.addTag(tag));
	}
	
	/**
	 * Check if tag exists
	 * @param tag anchor to be checked
	 * @return if an tag exists or not
	 */
	public boolean tagExists(Tag tag) {
		return this.listOfTags.containsKey(tag.getDeviceId());
	}
}
