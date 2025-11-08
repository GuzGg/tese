package pt.um.ucl.positioning.C03a.uwb.simulator;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualAnchorThread extends Thread {
	private VirtualAnchor anchor;
	private static final AtomicInteger count = new AtomicInteger(0); 
	private int anchorID = 0;
	
	private VirtualAnchorThread () {
		this.anchor = new VirtualAnchor("anchor"+anchorID, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
		this.anchorID = count.incrementAndGet(); 
	}
	
	public void run() {
		
	}
}