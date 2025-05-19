package teste;

import java.util.ArrayList;
import java.util.List;

public class Synchronizer {
	
	public List<Tag> listOfTags;
	public List<Anchor> listOfAnchors;
	
	public Synchronizer(List<Tag> listOfTags, List<Anchor> listOfAnchors) {
		super();
		this.listOfTags = listOfTags;
		this.listOfAnchors = listOfAnchors;
	}
	
	public Synchronizer() {
		this.listOfTags = new ArrayList<Tag>();
		this.listOfAnchors = new ArrayList<Anchor>();
	}
	
	public void addNewAnchor(Anchor anchor) {
		this.listOfAnchors.add(anchor);
	}
	
	public boolean anchorExists(Anchor anchor) {
		return this.listOfAnchors.indexOf(anchor) > 0;
	}
	
	public void addNewTag(Tag tag) {
		this.listOfTags.add(tag);
		this.listOfAnchors.forEach(anchor -> anchor.addTag(tag));
	}
	
	public boolean tagExists(Tag tag) {
		return this.listOfTags.indexOf(tag) > 0;
	}
	
	public boolean tagExists2(Tag tag) {
		boolean exists = this.listOfAnchors.stream().anyMatch((anchor -> anchor.listOfTags.contains(tag)));
		return exists;
	}
	
}
