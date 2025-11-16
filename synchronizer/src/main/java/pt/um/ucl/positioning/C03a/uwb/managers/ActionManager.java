package pt.um.ucl.positioning.C03a.uwb.managers;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Manages the sequencing of actions (slow scan, fast scan, measurement)
 * based on predefined time intervals. This class determines the next action
 * for the UWB system and calculates relevant timing information.
 * 
 * @author Gustavo Oliveira
 * @version 0.1
 */
public class ActionManager {
	
	/**
	 * Enumeration of possible actions the system can perform.
	 */
	public enum Action{
		/** A comprehensive but slow scan for new devices. */
		SLOW_SCAN,
		/** A quicker scan for devices. */
		FAST_SCAN,
		/** A distance measurement round with known devices. */
		MEASUREMENT
	}
	
	/** Time period between slow scans (milliseconds). */
	private long slowScanPeriod;
	/** Time period after which a new fast scan is triggered (milliseconds). */
	private long scanPeriod;
	/** Minimum time interval between scans (milliseconds). */
	private long scanInterval;
	/** The duration of a single scan operation (milliseconds). */
	private long scanTime;
	/** The timestamp of the last scan. */
	public LocalDateTime lastScan = LocalDateTime.now();
	/** Timestamp until which the communication channel is considered busy (milliseconds). */
	private long channelBusyUntil = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	/** Timestamp when the current action round started (milliseconds). */
	private long actionStartingTime = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	
	/**
	 * Default constructor that initializes the action periods with hard-coded values.
	 */
	public ActionManager() {
		this.slowScanPeriod = 60000; // milliseconds
		this.scanPeriod = 30000;    // milliseconds
		this.scanInterval = 2000;    // milliseconds
		this.scanTime = 300;       // milliseconds
	}
	
	/**
	 * Constructor that initializes action periods with values from a configuration source.
	 *
	 * @param slowScanPeriod The period for a slow scan in milliseconds.
	 * @param scanPeriod The period for a fast scan in milliseconds.
	 * @param scanInterval The time between consecutive scans in milliseconds.
	 * @param scanTime The duration of a single scan operation in milliseconds.
	 */
	public ActionManager(long slowScanPeriod, long scanPeriod, long scanInterval, long scanTime) {
		this.slowScanPeriod = slowScanPeriod;
		this.scanPeriod = scanPeriod;
		this.scanInterval = scanInterval;
		this.scanTime = scanTime;
	}
	
	/**
	 * Determines the next action for the UWB system based on time intervals.
	 *
	 * @return The next action, which can be SLOW_SCAN, FAST_SCAN, or MEASUREMENT.
	 */
	public Action nextAction() {
		long now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		long lastScanMillis = this.lastScan.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		
		// If the last scan was a long time ago (greater than scanPeriod), perform a fast scan
		if((now - lastScanMillis) > this.scanPeriod) {	
			this.lastScan = LocalDateTime.now();
			return Action.FAST_SCAN;
		} 
		// If the last scan was very recent (less than scanInterval), also perform a fast scan
		else if((now - lastScanMillis) < this.scanInterval) {
			return Action.FAST_SCAN;
		}
		
		// Otherwise, a measurement is the next action
		return Action.MEASUREMENT;
	}
	
	/**
	 * Calculates the time for the next slow scan.
	 * It also updates the 'channelBusyUntil' time.
	 *
	 * @return The time in milliseconds for the next slow scan action to be scheduled.
	 */
	public long getSlowScanTime(){
		// Update channel busy time by adding the duration of the slow scan period and scan time
		this.setChannelBusyUntil(this.getChannelBusyUntil() + (this.slowScanPeriod + this.scanTime));
		return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + this.slowScanPeriod/this.scanTime;
	}
	
	/**
	 * Calculates the time for the next fast scan.
	 * It also updates the 'channelBusyUntil' time.
	 *
	 * @return The time in milliseconds for the next fast scan action to be scheduled.
	 */
	public long getFastScanTime() {
		this.setChannelBusyUntil(this.getChannelBusyUntil() + this.scanTime);
		return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + this.scanPeriod/this.scanTime;
	}
	
	/**
	 * Calculates the measurement time for the next round based on the number of anchors and tags.
	 * It updates the 'channelBusyUntil' time and the action starting time.
	 *
	 * @param numberOfAnchor The number of anchors in the system.
	 * @param numberOfTags The number of tags in the system.
	 * @return The timestamp in milliseconds when the next measurement action should start.
	 */
	public long getMeasurmentTime(int numberOfAnchor, int numberOfTags) {
		long now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		long lastScanMillis = this.lastScan.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		
		// Check if a new measurement round should be initiated
		if((now - lastScanMillis) > this.scanPeriod) {
			this.setActionStartingTime(this.getChannelBusyUntil());		
			// Calculate the total time the channel will be busy for this measurement round
			this.setChannelBusyUntil(this.getChannelBusyUntil() + (long) (numberOfAnchor * numberOfTags * this.scanTime));
		}
		return this.getActionStartingTime();
	}

	/**
	 * Gets the timestamp when the communication channel will become free.
	 *
	 * @return The 'channelBusyUntil' timestamp in milliseconds.
	 */
	public long getChannelBusyUntil() {
		return channelBusyUntil;
	}

	/**
	 * Sets the timestamp for when the communication channel will become free.
	 *
	 * @param channelBusyUntil The new 'channelBusyUntil' timestamp in milliseconds.
	 */
	public void setChannelBusyUntil(long channelBusyUntil) {
		this.channelBusyUntil = channelBusyUntil;
	}

	/**
	 * Gets the timestamp when the current action started.
	 *
	 * @return The 'actionStartingTime' timestamp in milliseconds.
	 */
	public long getActionStartingTime() {
		return actionStartingTime;
	}

	/**
	 * Sets the timestamp for when the current action started.
	 *
	 * @param actionStartingTime The new 'actionStartingTime' timestamp in milliseconds.
	 */
	public void setActionStartingTime(long actionStartingTime) {
		this.actionStartingTime = actionStartingTime;
	}
	
	/**
	 * Gets the duration of a single scan operation.
	 *
	 * @return The scan time in milliseconds.
	 */
	public long getScanTime() {
		return this.scanTime;
	}
}