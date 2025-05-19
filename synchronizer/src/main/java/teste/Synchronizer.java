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
	
}
