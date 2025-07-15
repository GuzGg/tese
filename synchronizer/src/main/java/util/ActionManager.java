package util;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class ActionManager {
	public enum Action{
		SLOW_SCAN,
		FAST_SCAN,
		MEASUREMENT
	}
	
	private int slowScanPeriod = 60000;
	private int scanPeriod = 30000;
	private int scanInterval = 2000;
	private int scanTime = 300;
	public LocalDateTime lastScan = LocalDateTime.now();
	private long channelBusyUntil = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	
	public ActionManager() {
		
	}
	
	public Action nextAction() {
		if((LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - this.lastScan.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) > this.scanPeriod) {	
			this.lastScan = LocalDateTime.now();
			return Action.FAST_SCAN;
		} else if((LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - this.lastScan.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) < this.scanInterval) {
			return Action.FAST_SCAN;
		}
		return Action.MEASUREMENT;
	}
	
	public void updateChannelBusyUntilSlowScan(){
		this.setChannelBusyUntil(this.getChannelBusyUntil() + (this.slowScanPeriod + this.scanTime));
	}
	public void updateChannelBusyUntilFastScan() {
		this.setChannelBusyUntil(this.getChannelBusyUntil() + this.scanTime);
	}
	
	public void updateChanelBusyMeasurement(int numberOfAnchor, int numberOfTags) {
		if((LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - this.lastScan.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) > this.scanPeriod)
		this.setChannelBusyUntil(this.getChannelBusyUntil() + (numberOfAnchor * numberOfTags * this.scanTime));
	}

	public long getChannelBusyUntil() {
		return channelBusyUntil;
	}

	public void setChannelBusyUntil(long channelBusyUntil) {
		this.channelBusyUntil = channelBusyUntil;
	}
}
