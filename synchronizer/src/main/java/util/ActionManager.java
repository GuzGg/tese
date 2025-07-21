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
	private long actionStartingTime = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	
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
	
	public long getSlowScanTime(){
		this.setChannelBusyUntil(this.getChannelBusyUntil() + (this.slowScanPeriod + this.scanTime));
		return this.slowScanPeriod/this.scanTime;
	}
	public long getFastScanTime() {
		this.setChannelBusyUntil(this.getChannelBusyUntil() + this.scanTime);
		return this.scanPeriod/this.scanTime;
	}
	
	public long getMeasurmentTime(int numberOfAnchor, int numberOfTags) {
		if((LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - this.lastScan.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) > this.scanPeriod) {
			this.setChannelBusyUntil(this.getChannelBusyUntil() + (numberOfAnchor * numberOfTags * this.scanTime));
			this.setActionStartingTime(this.getChannelBusyUntil());
		}
		return this.getActionStartingTime();
	}

	public long getChannelBusyUntil() {
		return channelBusyUntil;
	}

	public void setChannelBusyUntil(long channelBusyUntil) {
		this.channelBusyUntil = channelBusyUntil;
	}

	public long getActionStartingTime() {
		return actionStartingTime;
	}

	public void setActionStartingTime(long actionStartingTime) {
		this.actionStartingTime = actionStartingTime;
	}
	
	public long getScanTime() {
		return this.scanTime;
	}
}
