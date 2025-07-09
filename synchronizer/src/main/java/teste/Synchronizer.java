package teste;

import java.util.HashMap;
import java.util.Map;

import devices.Anchor;
import devices.Tag;

public class Synchronizer {
	
	public Map<String, Tag> listOfTags;
	public  Map<String, Anchor> listOfAnchors;
	// tagId -> {anchorId -> distance}
	public Map<String, Map<String, Double>>  distances;
	
	public Synchronizer(Map<String, Tag> listOfTags, Map<String, Anchor> listOfAnchors) {
		super();
		this.listOfTags = listOfTags;
		this.listOfAnchors = listOfAnchors;
		this.distances = new HashMap<String, Map<String, Double>>();
		this.listOfTags.forEach(
			(tagString, tag) -> {
				this.listOfAnchors.forEach(
					(anchorString, anchor) -> {
						HashMap auxMap = new HashMap<String, Double>();
						auxMap.put(anchorString, Double.MAX_VALUE);
						this.distances.put(tagString, auxMap);
					}
				);
			}
		);
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
	
	/**
	 * Updates the nearest anchor for a given tag based on the distances stored
	 * in the 'distances' map.
	 * @param tagId unique ID of the tag whose nearest anchor needs to be updated.
	 */
	public void updateNearestAnchor(String tagId) {
		if (distances == null || !distances.containsKey(tagId)) {
			System.err.println("No distance data available or tag '" + tagId + "' not found in distances map.");
			return;
		}

		Map<String, Double> tagDistances = distances.get(tagId);

		if (tagDistances == null || tagDistances.isEmpty()) {
			System.err.println("No anchor distances recorded for tag '" + tagId + "'. Cannot update nearest anchor.");
			return;
		}

		String nearestAnchorId = null;
		double minDistance = Double.MAX_VALUE; 

		for (Map.Entry<String, Double> entry : tagDistances.entrySet()) {
			String currentAnchorId = entry.getKey();
			Double currentDistance = entry.getValue();
			if (currentDistance != null && currentDistance < minDistance) {
				minDistance = currentDistance;
				nearestAnchorId = currentAnchorId;
			}
		}
		
		if (nearestAnchorId != null) {
			Tag tag = listOfTags.get(tagId);
			if (tag == null) {
				return;
			}
			
			tag.setNearestAnchor(listOfAnchors.get(nearestAnchorId));
		}
	}
	
	
}
