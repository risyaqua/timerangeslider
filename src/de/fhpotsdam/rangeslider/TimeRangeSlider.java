package de.fhpotsdam.rangeslider;

import java.lang.reflect.Method;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Seconds;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PFont;
import de.fhpotsdam.utils.FontManager;

/**
 * A user interface component to enable selecting and animating time ranges. This TimeRangeSlider handles both mouse and
 * keyboard input, and adapts the display accordingly. Firstly, the user can select the time window, as well as the
 * length of the time range. Secondly, this component can be used to handle animation, by running step-wise through the
 * time. If a {@link TimeRangeSliderListener} has been specified, it will be notified of any time updates, whether by
 * user interaction or by animation.
 * 
 * TODO tn, 28 Nov 2011: Copy and adapt JavaDoc from old TimeRangeUtils.
 * 
 * Copyright (c) 2015 Till Nagel, tillnagel.com
 * 
 */
public class TimeRangeSlider {

	protected PApplet p;

	// Time and ranges --------------------------

	/** Start time of overall time, i.e. the full range to select sub ranges in. */
	protected DateTime startDateTime;
	/** End time of overall time, i.e. the full range to select sub ranges in. */
	protected DateTime endDateTime;

	/** Time interval of the range. */
	protected int aggregationIntervalSeconds = 60;
	/** Time interval for the animation. */
	protected int animationIntervalSeconds;
	/** Time interval for the tick marks. Also used for interaction. */
	protected int tickIntervalSeconds;

	/** Start of selected time range. */
	protected DateTime currentStartDateTime;
	/**
	 * End of selected time range. Is automatically set via currentStartDateTime and aggregationIntervalSeconds.
	 */
	protected DateTime currentEndDateTime;

	// Overall time range in seconds
	protected int totalSeconds;
	// Time range between ticks
	protected float widthPerSecond;

	// Current x position of the range start
	protected float currentStartX;
	// Current x position of the range end
	protected float currentEndX;

	// Animation speed
	protected int framesPerInterval = 10;
	// Whether slider is currently animated
	protected boolean running = false;

	// Display properties -----------------------

	/** Whether to show tick markers. */
	protected boolean showTicks = true;

	/** Shows labels for start and end times. */
	protected boolean showStartEndTimeLabels = true;
	/** Shows labels for selected time range. */
	protected boolean showTimeRangeLabels = true;
	protected String timeLabelFormat = "HH:mm";
	protected float labelPadding = 6;
	protected boolean showSelectedTimeRange = true;

	// Position and dimension -------------------

	protected float x;
	protected float y;
	protected float width;
	protected float height;

	// Handles ----------------------------------

	protected boolean centeredHandle = true;
	protected boolean draggedSelectedTimeRange = false;
	protected boolean draggedStartHandle = false;
	protected boolean draggedEndHandle = false;
	protected float startHandleX;
	protected float endHandleX;
	protected float handleWidth;
	protected float handleHeight;

	protected boolean inProximity = false;
	protected float inProximityPadding = 25;
	protected boolean alwaysShowHandles = false;

	// Interaction ------------------------------

	/** Will be called if slider has been updated. */
	protected TimeRangeSliderListener listener;

	// For multitouch purposes, i.e. to allow multiple dragging at the same time
	private String startHandleId = null;
	private String endHandleId = null;
	private String timeRangeHandleId = null;
	protected static final String MOUSE_ID = "mouse";

	// Event ------------------------------------
	private Method timeUpdatedMethod;

	// ------------------------------------------
	
	boolean isSingleSlider = false;


	/**
	 * Creates a TimeRangeSlider.
	 * 
	 * @param p
	 *            The PApplet. Optionally, this is also a TimeRangeSlider.
	 * @param x
	 *            The x position of this UI.
	 * @param y
	 *            The y position of this UI.
	 * @param width
	 *            The width of this UI.
	 * @param height
	 *            The height of this UI.
	 * @param startDateTime
	 *            The overall start time of this slider.
	 * @param endDateTime
	 *            The overall end time of this slider.
	 * @param aggregationIntervalSeconds
	 *            The number of seconds for every interval. Will be used for the tics as well. Use
	 *            {@link #setTickIntervalSeconds(int)} for different intervals.
	 */
	public TimeRangeSlider(PApplet p, float x, float y, float width, float height, DateTime startDateTime,
			DateTime endDateTime, int aggregationIntervalSeconds) {
		this.p = p;
		if (p instanceof TimeRangeSliderListener) {
			this.listener = (TimeRangeSliderListener) p;
		}
		FontManager.getInstance(p);

		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;

		this.startDateTime = startDateTime;
		this.endDateTime = endDateTime;
		this.currentStartDateTime = startDateTime;
		this.currentEndDateTime = this.currentStartDateTime.plusSeconds(aggregationIntervalSeconds);

		this.aggregationIntervalSeconds = aggregationIntervalSeconds;
		this.tickIntervalSeconds = aggregationIntervalSeconds;
		this.animationIntervalSeconds = aggregationIntervalSeconds;

		handleWidth = 8;
		handleHeight = height;

		totalSeconds = Seconds.secondsBetween(startDateTime, endDateTime).getSeconds();
		widthPerSecond = width / totalSeconds;

		// event hook
		try {
			timeUpdatedMethod = p.getClass().getMethod("timeUpdated", new Class[] { DateTime.class, DateTime.class });
		} catch (Exception e) {
		}

	}

	public void setTickIntervalSeconds(int tickIntervalSeconds) {
		this.tickIntervalSeconds = tickIntervalSeconds;
		this.animationIntervalSeconds = tickIntervalSeconds;
	}

	public void setAnimationIntervalSeconds(int animationIntervalSeconds) {
		this.animationIntervalSeconds = animationIntervalSeconds;
	}

	public void addAnimationIntervalSeconds(int diffAnimationIntervalSeconds) {
		animationIntervalSeconds += diffAnimationIntervalSeconds;
	}

	public void update() {
		if ((p.frameCount % framesPerInterval == 0) && running) {
			nextAnimationStep();
		}
	}

	/**
	 * Draws this TimeRangeSlider.
	 */
	public void draw() {
		update();

		drawTimeLine();
		drawStartAndEndTics();

		// Show tics
		if (showTicks) {
			float distancePerTic = widthPerSecond * tickIntervalSeconds;
			for (float tx = x; tx < x + width; tx += distancePerTic) {
				drawTic(tx);
			}
		}

		int currentStartSeconds = Seconds.secondsBetween(startDateTime, currentStartDateTime).getSeconds();
		int currentEndSeconds = Seconds.secondsBetween(startDateTime, currentEndDateTime).getSeconds();
		currentStartX = x + widthPerSecond * currentStartSeconds;
		currentEndX = x + widthPerSecond * currentEndSeconds;

		// Show selected time range
		if (showSelectedTimeRange) {
			drawSelectedTimeRange(draggedSelectedTimeRange);
		}

		// Show handles to change time range
		startHandleX = currentStartX;
		endHandleX = currentEndX;
		if (centeredHandle) {
			startHandleX -= handleWidth / 2;
			endHandleX -= handleWidth / 2;
		}

		if (inProximity || alwaysShowHandles) {
			drawHandle(startHandleX, draggedStartHandle, true);
			drawHandle(endHandleX, draggedEndHandle, false);
		}

		// Show labels for selected time range
		if (showTimeRangeLabels) {
			drawTimeRangeLabels();
		}
		// Show labels for complete time
		if (showStartEndTimeLabels) {
			drawStartEndTimeLabels();
		}
	}

	protected void drawStartEndTimeLabels() {
		PFont font = FontManager.getInstance().getLabelFont();
		p.fill(0, 200);
		p.textFont(font);

		String startTimeLabel = startDateTime.toString("HH:mm");
		int startLabelX = (int) (x - p.textWidth(startTimeLabel) - labelPadding);
		int labelY = (int) (y + font.getSize() / 2 - 3);
		p.text(startTimeLabel, startLabelX, labelY);

		String endTimeLabel = endDateTime.toString("HH:mm");
		int endLabelX = (int) (x + width + labelPadding);
		p.text(endTimeLabel, endLabelX, labelY);
	}

	protected void drawTimeRangeLabels() {
		String timeRangeLabel = getTimeRangeLabel();
		PFont font = FontManager.getInstance().getLabelFont();
		p.textFont(font);
		int labelX = (int) (currentStartX + (currentEndX - currentStartX) / 2 - p.textWidth(timeRangeLabel) / 2);
		int labelY = (int) (y + font.getSize() + labelPadding / 2);
		drawLabel(timeRangeLabel, labelX, labelY);
	}
	
	protected String getTimeRangeLabel() {
		return currentStartDateTime.toString(timeLabelFormat) + " - " + currentEndDateTime.toString(timeLabelFormat);
	}

	public void setTimeLabelFormatString(String timeLabelFormat) {
		this.timeLabelFormat = timeLabelFormat;
	}


	protected void drawLabel(String timeRangeLabels, int labelX, int labelY) {
		p.fill(66);
		p.text(timeRangeLabels, labelX, labelY);
	}

	protected void drawTic(float tx) {
		float tyTop = y - 4;
		float tyBottom = y + 4;
		p.stroke(0, 50);
		p.line(tx, tyTop, tx, tyBottom);
	}

	protected void drawStartAndEndTics() {
		float yTop = y - height / 2;
		float yBottom = y + height / 2;
		p.line(x, yTop, x, yBottom);
		p.line(x + width, yTop, x + width, yBottom);
	}

	protected void drawSelectedTimeRange(boolean highlight) {
		float yTop = y - height / 2;
		if (highlight) {
			p.fill(200, 66, 66, 200);
		} else {
			p.fill(66, 200);
		}
		p.rect(currentStartX, yTop + height / 4, currentEndX - currentStartX, height / 2);
	}

	protected void drawTimeLine() {
		p.stroke(0);
		p.noFill();
		p.line(x, y, x + width, y);
	}

	protected void drawHandle(float handleX, boolean highlight, boolean start) {
		p.fill(250, 220);
		if (highlight) {
			p.stroke(140, 20, 20, 150);
		} else {
			p.stroke(140);
		}

		float handleY = y - height / 2;
		p.rect(handleX, handleY, handleWidth, handleHeight);
		p.line(handleX + 3, handleY + 4, handleX + 3, handleY + 12);
		p.line(handleX + 5, handleY + 4, handleX + 5, handleY + 12);
	}

	// --------------------------------------------------------------

	/**
	 * Sets the current time range. Does not check for validity in respect to overall time range! As the range is based
	 * on the aggregationIntervalSeconds, this method sets the currentStartDateTime and the aggregationIntervalSeconds
	 * (which results in a new currentEndDateTime).
	 * 
	 * @param newStartDateTime
	 *            The start date time to set for the current time range
	 * @param newEndDateTime
	 *            The end date time to set for the current time range.
	 */
	public void setCurrentRange(DateTime newStartDateTime, DateTime newEndDateTime) {
		currentStartDateTime = newStartDateTime.plus(0);
		int newAggregationIntervalSeconds = Seconds.secondsBetween(newStartDateTime, newEndDateTime).getSeconds();
		aggregationIntervalSeconds = newAggregationIntervalSeconds;
		updateAnimationStep();
	}

	/**
	 * Sets the current start time. This shifts the time range, i.e. the length of the range does not change. Use
	 * {@link #setCurrentRange(DateTime, DateTime)} to modify the range.
	 * 
	 * @param newStartDateTime
	 *            The start date time to set for the current time range.
	 */
	public void setCurrentStartDateTime(DateTime newStartDateTime) {
		currentStartDateTime = newStartDateTime.plus(0);
		updateAnimationStep();
	}

	/**
	 * Sets the current end time. This shifts the time range, i.e. the length of the range does not change. Use
	 * {@link #setCurrentRange(DateTime, DateTime)} to modify the range.
	 * 
	 * @param newStartDateTime
	 *            The start date time to set for the current time range.
	 */
	public void setCurrentEndDateTime(DateTime newEndDateTime) {
		int diffSeconds = Seconds.secondsBetween(currentEndDateTime, newEndDateTime).getSeconds();
		currentStartDateTime = currentStartDateTime.plusSeconds(diffSeconds);
		updateAnimationStep();
	}

	/**
	 * Goes to next animations step, i.e. slides the time by animationIntervalSeconds.
	 */
	public void nextAnimationStep() {
		currentStartDateTime = currentStartDateTime.plusSeconds(animationIntervalSeconds);
		if (currentStartDateTime.isAfter(endDateTime.minusSeconds(aggregationIntervalSeconds))) {
			currentStartDateTime = startDateTime;
		}
		updateAnimationStep();
	}

	/**
	 * Goes to previous animations step, i.e. slides the time by -animationIntervalSeconds.
	 */
	public void previousAnimationStep() {
		currentStartDateTime = currentStartDateTime.minusSeconds(animationIntervalSeconds);
		if (currentStartDateTime.isBefore(startDateTime)) {
			currentStartDateTime = endDateTime.minusSeconds(aggregationIntervalSeconds);
		}
		updateAnimationStep();
	}

	/**
	 * Goes to next interval step, i.e. slides the time by aggregationIntervalSeconds.
	 */
	public void nextInterval() {
		currentStartDateTime = currentStartDateTime.plusSeconds(aggregationIntervalSeconds);
		if (currentStartDateTime.isAfter(endDateTime.minusSeconds(aggregationIntervalSeconds))) {
			currentStartDateTime = startDateTime;
		}
		updateAnimationStep();
	}

	/**
	 * Goes to previous interval step, i.e. slides the time by -aggregationIntervalSeconds.
	 */
	public void previousInterval() {
		currentStartDateTime = currentStartDateTime.minusSeconds(aggregationIntervalSeconds);
		if (currentStartDateTime.isBefore(startDateTime)) {
			currentStartDateTime = endDateTime.minusSeconds(aggregationIntervalSeconds);
		}
		updateAnimationStep();
	}

	/**
	 * Increases the current time range by the current tickIntervalSeconds.
	 */
	public void increaseRange() {
		aggregationIntervalSeconds += tickIntervalSeconds;
		updateAnimationStep();
	}

	/**
	 * Increases the current time range by the given seconds.
	 * 
	 * @param increaseIntervalSeconds
	 *            The seconds to increase the range by.
	 */
	public void increaseRangeBy(int increaseIntervalSeconds) {
		aggregationIntervalSeconds += increaseIntervalSeconds;
		updateAnimationStep();
	}

	protected void updateAnimationStep() {
		currentEndDateTime = currentStartDateTime.plusSeconds(aggregationIntervalSeconds);
		fireAnimationStepListeners();
	}
	
	protected void fireAnimationStepListeners() {
		// Two event mechanisms: Listener or Reflection
		if (listener != null) {
			// Call implemented method of listener

			// FIXME timeUpdated is called too often from TimeRangeSlider (even if not updated)
			listener.timeUpdated(currentStartDateTime, currentEndDateTime);

		} else if (timeUpdatedMethod != null) {
			// Call method of applet if implemented
			try {
				timeUpdatedMethod.invoke(p, new Object[] { currentStartDateTime, currentEndDateTime });
			} catch (Exception e) {
				System.err.println("Disabling timeUpdatedMethod()");
				e.printStackTrace();
				timeUpdatedMethod = null;
			}
		}
	}

	public void playOrPause() {
		running = !running;
	}

	public void play() {
		running = true;
	}

	public void pause() {
		running = false;
	}

	// Interactions -------------------------------------------------

	public void onClicked(int checkX, int checkY) {
		onMoved(checkX, checkY);
	}
	
	public void onMoved(int checkX, int checkY) {
		inProximity = checkX > x - inProximityPadding && checkX < x + width + inProximityPadding
				&& checkY > y - height / 2 - inProximityPadding && checkY < y + height / 2 + inProximityPadding;

		// Checks whether the main selector is moved
		draggedSelectedTimeRange = isOverTimeRange(checkX, checkY);

		draggedStartHandle = isOverStartHandle(checkX, checkY);
		draggedEndHandle = isOverEndHandle(checkX, checkY);

		onAdded(checkX, checkY, MOUSE_ID);
	}

	protected boolean isOverTimeRange(int checkX, int checkY) {
		float handlePadding = (centeredHandle) ? handleWidth / 2 : handleWidth;
		float yTop = y - height / 2;
		float yBottom = y + height / 2;
		return checkX > currentStartX + handlePadding && checkX < currentEndX - handlePadding && checkY > yTop
				&& checkY < yBottom;
	}

	protected boolean isOverStartHandle(int checkX, int checkY) {
		float handleY = y - height / 2;
		return checkX > startHandleX && checkX < startHandleX + handleWidth && checkY > handleY
				&& checkY < handleY + handleHeight;
	}

	protected boolean isOverEndHandle(int checkX, int checkY) {
		float handleY = y - height / 2;
		return checkX > endHandleX && checkX < endHandleX + handleWidth && checkY > handleY
				&& checkY < handleY + handleHeight;
	}

	public void onAdded(int checkX, int checkY, String id) {
		// Allow only one interaction at a time; either dragging handles OR timeRange.

		if (isOverStartHandle(checkX, checkY) && !draggedSelectedTimeRange) {
			draggedStartHandle = true;
			startHandleId = id;
		}

		if (isOverEndHandle(checkX, checkY) && !draggedSelectedTimeRange) {
			draggedEndHandle = true;
			endHandleId = id;
		}

		if (isOverTimeRange(checkX, checkY) && !draggedStartHandle && !draggedEndHandle) {
			draggedSelectedTimeRange = true;
			timeRangeHandleId = id;
		}
	}

	public void onReleased(int checkX, int checkY) {
		onReleased(checkX, checkY, MOUSE_ID);
	}
	
	public void onReleased(int checkX, int checkY, String id) {
		if (id.equals(startHandleId)) {
			draggedStartHandle = false;
			startHandleId = null;
		}
		if (id.equals(endHandleId)) {
			draggedEndHandle = false;
			endHandleId = null;
		}
		if (id.equals(timeRangeHandleId)) {
			draggedSelectedTimeRange = false;
			timeRangeHandleId = null;
		}
	}

	public void onDragged(float checkX, float checkY, float oldX, float oldY) {
		onDragged(checkX, checkY, oldX, oldY, MOUSE_ID);
	}

	public void onDragged(float checkX, float checkY, float oldX, float oldY, String id) {

		float widthPerTic = widthPerSecond * tickIntervalSeconds;

		int currentStartSeconds = Seconds.secondsBetween(startDateTime, currentStartDateTime).getSeconds();
		int currentEndSeconds = Seconds.secondsBetween(startDateTime, currentEndDateTime).getSeconds();
		currentStartX = x + widthPerSecond * currentStartSeconds;
		currentEndX = x + widthPerSecond * currentEndSeconds;

		if (draggedEndHandle && id.equals(endHandleId) && !draggedStartHandle) {
			float tx = PApplet.constrain(checkX, x, x + width);
			tx = Math.round((tx - currentStartX) / widthPerTic) * widthPerTic;
			int seconds = Math.round(tx / widthPerSecond);
			// Update if larger than first tick, and different to prev value
			if (seconds >= tickIntervalSeconds && seconds != aggregationIntervalSeconds) {
				aggregationIntervalSeconds = seconds;
				updateAnimationStep();
			}
		}

		if (draggedStartHandle && id.equals(startHandleId)) {
			float tx = PApplet.constrain(checkX, x, x + width);
			tx = Math.round((currentEndX - tx) / widthPerTic) * widthPerTic;
			int seconds = Math.round(tx / widthPerSecond);
			if (seconds >= tickIntervalSeconds && seconds != aggregationIntervalSeconds) {
				aggregationIntervalSeconds = seconds;
				if (isSingleSlider && currentStartDateTime.isEqual(currentEndDateTime.minusSeconds(aggregationIntervalSeconds))) {
					currentStartDateTime = currentEndDateTime.plus(0);
				} else {
					currentStartDateTime = currentEndDateTime.minusSeconds(aggregationIntervalSeconds);
				}
				updateAnimationStep();
			}
		}

		if (draggedSelectedTimeRange && timeRangeHandleId != null && timeRangeHandleId.equals(id)) {
			// TODO tn, Oct 7, 2011: Move slider correctly if borders are hit (use onClick and
			// onRelease)

			checkX = Math.round(checkX / widthPerTic) * widthPerTic;
			oldX = Math.round(oldX / widthPerTic) * widthPerTic;
			float diffX = checkX - oldX;

			if (currentStartX + diffX < x || currentEndX + diffX > x + width) {
				diffX = 0;
			}

			int seconds = Math.round(diffX / widthPerSecond);
			if (Math.abs(seconds) >= tickIntervalSeconds) {
				// if (Math.abs(seconds) >= aggregationIntervalSeconds) {
				currentStartDateTime = currentStartDateTime.plusSeconds(seconds);
				updateAnimationStep();
			}
		}
	}

	public void onKeyPressed(char key, int keyCode) {
		if (key == ' ') {
			playOrPause();
		}
		if (key == PConstants.CODED) {
			if (keyCode == PConstants.LEFT) {
				previousAnimationStep();
			}
			if (keyCode == PConstants.RIGHT) {
				nextAnimationStep();
			}
		}
	}

	/**
	 * Gets the current start date time. Convenience for {@link #getCurrentStartDateTime()}.
	 * 
	 * @return The current start date time.
	 */
	public DateTime getCurrentDateTime() {
		return currentStartDateTime;
	}

	/**
	 * Gets the current start date time, i.e. beginning of current time range.
	 * 
	 * @return The current start date time.
	 */
	public DateTime getCurrentStartDateTime() {
		return currentStartDateTime;
	}

	/**
	 * Gets the current end date time, i.e. end of current time range.
	 * 
	 * @return The current end date time.
	 */
	public DateTime getCurrentEndDateTime() {
		return currentEndDateTime;
	}

	/**
	 * Gets the overall start date time, i.e. beginning of whole time line.
	 * 
	 * @return The overall start date time.
	 */
	public DateTime getStartDateTime() {
		return startDateTime;
	}

	/**
	 * Gets the overall end date time, i.e. end of whole time line.
	 * 
	 * @return The overall end date time.
	 */
	public DateTime getEndDateTime() {
		return endDateTime;
	}

	/**
	 * Returns the current interval, i.e. the Interval between current start and end dateTimes. Is useful to check
	 * whether a DateTime is between the current time range (with interval.contains).
	 * 
	 * @return The current interval.
	 */
	public Interval getCurrentInterval() {
		return new Interval(getCurrentStartDateTime(), getCurrentEndDateTime());
	}

	public void setShowTicks(boolean showTicks) {
		this.showTicks = showTicks;
	}

	public void setShowStartEndTimeLabels(boolean showStartEndTimeLabels) {
		this.showStartEndTimeLabels = showStartEndTimeLabels;
	}

	public void setShowTimeRangeLabels(boolean showTimeRangeLabels) {
		this.showTimeRangeLabels = showTimeRangeLabels;
	}

	public void setShowSelectedTimeRange(boolean showSelectedTimeRange) {
		this.showSelectedTimeRange = showSelectedTimeRange;
	}

	public void setInProximityPadding(float inProximityPadding) {
		this.inProximityPadding = inProximityPadding;
	}

	public void setAlwaysShowHandles(boolean alwaysShowHandles) {
		this.alwaysShowHandles = alwaysShowHandles;
	}

	public boolean isPlaying() {
		return running;
	}

	/**
	 * Sets animation delay. The higher the value the slower, and the lower the value the faster the animation.
	 * 
	 * Not to be confused with animationIntervalSeconds! (Which sets the seconds for each animation step.)
	 * 
	 * @param framesPerInterval
	 *            Specified how many frames to wait per animation interval.
	 */
	public void setAnimationDelay(int framesPerInterval) {
		this.framesPerInterval = framesPerInterval;
	}

}
