package managers;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class ActionManager {
	public enum Action{
		SLOW_SCAN,
		FAST_SCAN,
		MEASUREMENT
	}
	
	private int slowScanPeriod = 60000; //milliseconds
	private int scanPeriod = 30000; //milliseconds
	private int scanInterval = 2000; //milliseconds
	private int scanTime = 300; //milliseconds
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
		return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + this.slowScanPeriod/this.scanTime;
	}
	public long getFastScanTime() {
		this.setChannelBusyUntil(this.getChannelBusyUntil() + this.scanTime);
		return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + this.scanPeriod/this.scanTime;
	}
	
	public long getMeasurmentTime(int numberOfAnchor, int numberOfTags) {
		if((LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - this.lastScan.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) > this.scanPeriod) {
			this.setActionStartingTime(this.getChannelBusyUntil());		
			this.setChannelBusyUntil(this.getChannelBusyUntil() + (numberOfAnchor * numberOfTags * this.scanTime));
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
