package pt.um.ucl.positioning.C03a.uwb.managers;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Manages the sequencing of actions (slow scan, fast scan, measurement)
 * based on predefined time intervals. This class determines the next action
 * for the UWB system and calculates relevant timing information.
 * 
 * @author Gustavo Oliveira
 * @version 0.6
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
	
	private long minRoundTime;
	
	private String currentAction = "slow scan";
	
	/**
	 * Default constructor that initializes the action periods with hard-coded values.
	 */
	public ActionManager() {
		this.slowScanPeriod = 60000; // milliseconds
		this.scanPeriod = 30000;    // milliseconds
		this.scanInterval = 2000;    // milliseconds
		this.scanTime = 300;       // milliseconds
		this.minRoundTime = 1000; // milliseconds
	}
	
	/**
	 * Constructor that initializes action periods with values from a configuration source.
	 *
	 * @param slowScanPeriod The period for a slow scan in milliseconds.
	 * @param scanPeriod The period for a fast scan in milliseconds.
	 * @param scanInterval The time between consecutive scans in milliseconds.
	 * @param scanTime The duration of a single scan operation in milliseconds.
	 */
	public ActionManager(long slowScanPeriod, long scanPeriod, long scanInterval, long scanTime, long minRoundTime) {
		this.slowScanPeriod = slowScanPeriod;
		this.scanPeriod = scanPeriod;
		this.scanInterval = scanInterval;
		this.scanTime = scanTime;
		this.minRoundTime = minRoundTime;
	}
	
	/**
	 * Determines the next action for the UWB system based on time intervals.
	 *
	 * @return The next action, which can be SLOW_SCAN, FAST_SCAN, or MEASUREMENT.
	 */
		public Action nextAction() {
			long now = System.currentTimeMillis();

			// 1. Lock the channel for ALL action types, not just measurements.
			// If an action is currently running, do not interrupt it!
			if (now < this.channelBusyUntil) {
			    if ("measure".equals(this.currentAction)) return Action.MEASUREMENT;
			    if ("fast scan".equals(this.currentAction)) return Action.FAST_SCAN;
			    if ("slow scan".equals(this.currentAction)) return Action.SLOW_SCAN;
			}

			long lastScanMillis = this.lastScan.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			
			// 2. Trigger a scan EXACTLY ONCE when the period expires
			if((now - lastScanMillis) > this.scanPeriod) {	
				this.lastScan = LocalDateTime.now();
				this.setCurrentAction("fast scan");
				return Action.FAST_SCAN;
			} 
			
			// 3. Otherwise, safely run measurements
			this.setCurrentAction("measure");
			return Action.MEASUREMENT;
		}
		
		public long getFastScanTime() {
		    long now = System.currentTimeMillis();

		    // Synchronize fast scans just like measurements!
		    if (now >= this.channelBusyUntil) {
		        // Give anchors the full minRoundTime (1 second) to receive the command safely
		        this.setActionStartingTime(now + this.getMinRoundTime()); 
		        // Lock the channel for the duration of the scan
		        this.setChannelBusyUntil(this.getActionStartingTime() + this.scanTime);
		    }
		    
		    return this.getActionStartingTime();
		}

		public long getSlowScanTime(){
		    long now = System.currentTimeMillis();

		    if (now >= this.channelBusyUntil) {
		        this.setActionStartingTime(now + this.getMinRoundTime());
		        // Slow scans take longer, lock the channel appropriately
		        this.setChannelBusyUntil(this.getActionStartingTime() + this.slowScanPeriod);
		    }
		    
		    return this.getActionStartingTime();
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
	    long now = System.currentTimeMillis();

	    if (now >= this.channelBusyUntil) {
	        	        this.setActionStartingTime(now + this.getMinRoundTime()); 
	        	        long cycleDuration = (long) (numberOfAnchor * numberOfTags * this.scanTime);
	        
	        // Lock the channel for the entire duration of this cycle
	        this.setChannelBusyUntil(this.getActionStartingTime() + cycleDuration);
	    }

	    return this.getActionStartingTime();
	}
	
	/**
	 * Forces a time sync
	 */
	public void forceTimeSync() {
	    long now = System.currentTimeMillis();
	    if (this.channelBusyUntil < now) {
	        this.channelBusyUntil = now;
	        this.actionStartingTime = now;
	    }
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
	
	public void setCurrentAction(String action) {
		this.currentAction = action;
	}
	
	public String getCurrentAction() {
		return this.currentAction;
	}
	
	public long getMinRoundTime() {
		return this.minRoundTime;
	}
}