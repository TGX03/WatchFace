package de.tgx03.watchface;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class WatchFace extends CanvasWatchFaceService {

    private static final String TAG = "WatchFace";

    private static Engine engine;

    // Updates rate in milliseconds for interactive mode
    private static final short INTERACTIVE_UPDATE_RATE_MS = 1000;

    private static boolean complicationsInAmbient = true;

    // Complication IDs
    protected static final byte BACKGROUND_COMPLICATION = 0;
    protected static final byte TOP_COMPLICATION = 1;
    protected static final byte BOTTOM_LARGE_COMPLICATION = 2;
    protected static final byte BOTTOM_LEFT_COMPLICATION = 3;
    protected static final byte BOTTOM_MIDDLE_COMPLICATION = 4;
    protected static final byte BOTTOM_RIGHT_COMPLICATION = 5;

    private static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            // Background Complication
            {ComplicationData.TYPE_LARGE_IMAGE},
            // Big top complication
            {ComplicationData.TYPE_LONG_TEXT,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_SMALL_IMAGE},
            // Small bottom complications
            {ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_SMALL_IMAGE}};

    private final ComplicationDrawable[] complicationDrawables = new ComplicationDrawable[6];
    private final ComplicationData[] complicationData = new ComplicationData[6];

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final byte MSG_UPDATE_DISPLAY = 0;

    @Override
    public Engine onCreateEngine() {
        engine = new Engine();
        return engine;
    }

    /**
     * Retrieves the last created engine. Only gets used for creating screenshots in the settings.
     * Throws errors when no engine has been created. Only to be used in the package
     * @return The last created engine
     * @throws IllegalStateException Gets thrown when an engine hasn't yet been created
     */
    protected static Engine getEngine() throws IllegalStateException {
        if (engine != null) {
            return engine;
        } else {
            throw new IllegalStateException("Currently no engine available");
        }
    }

    protected static int[] getSupportedComplications(byte id) {
        switch (id) {
            case BACKGROUND_COMPLICATION:
                return COMPLICATION_SUPPORTED_TYPES[0];
            case TOP_COMPLICATION:
            case BOTTOM_LARGE_COMPLICATION:
                return COMPLICATION_SUPPORTED_TYPES[1];
            case BOTTOM_LEFT_COMPLICATION:
            case BOTTOM_RIGHT_COMPLICATION:
            case BOTTOM_MIDDLE_COMPLICATION:
                return COMPLICATION_SUPPORTED_TYPES[2];
            default:
                return new int[0];
        }
    }

    /**
     * This method sets whether complications should be rendered in ambient mode
     * @param enabled Whether complications shall be shown in ambient mode
     */
    protected static void setComplicationsInAmbient(boolean enabled) {
        Log.d(TAG, "Complications in ambient " + (enabled ? "enabled" : "disabled"));
        complicationsInAmbient = enabled;
    }

    /**
     * Returns whether complications currently get rendered in ambient mode
     * @return Whether complications are currently enabled in ambient mode
     */
    protected static boolean getComplicationsInAmbient() {
        return complicationsInAmbient;
    }

    protected class Engine extends CanvasWatchFaceService.Engine {

        private static final String TAG = "WatchFace.Engine";

        // The device features
        private boolean requiredBurnInProtection = true;   // True by default to not damage screen before actual data provided
        private boolean lastMovedRight = false; // Used to alternatingly move stuff right or left

        // Whether this is currently registered for receiving timezone changes
        private boolean receiving;
        // The filter for receiving timezone changes
        private final IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);

        private Integer screenWidth;
        private Integer screenHeight;

        // Values for time position and size
        private float timeX;
        private float timeY;
        private float timeXBurnIn;
        private static final float DEFAULT_TIME_Y = 0.47f;
        private static final float DEFAULT_TIME_SIZE = 0.3f;
        private static final float DEFAULT_SECONDS_SIZE = 0.09f;

        // Values for seconds position and size
        private float secondsX;

        // Values for date position and size
        private String lastDate;
        private float dateX;
        private float dateY;
        private float dateXBurnIn;
        private static final float DEFAULT_DATE_VERTICAL_OFFSET = 0.04f;
        private static final float DEFAULT_DATE_SIZE = 0.07f;

        // Styles for elements
        private final Paint background = new Paint();
        private final Paint timePaint = new Paint();
        private final Paint timePaintAmbient = new Paint();
        private final Paint datePaint = new Paint();
        private final Paint datePaintAmbient = new Paint();
        private final Paint secondsPaint = new Paint();

        private final Calendar calendar = Calendar.getInstance();

        private final Handler updateTimeHandler = new UpdateTimeHandler(new WeakReference<>(this));

        private boolean validBackground = false;

        // Left and right boundaries for large complications
        private static final float LARGE_COMPLICATION_LEFT = 0.2f;
        private static final float LARGE_COMPLICATION_RIGHT = 0.8f;

        // The coordinates for the large top complication
        private static final float TOP_COMPLICATION_TOP = 0.08f;
        private static final float TOP_COMPLICATION_BOTTOM = 0.23f;
        private Rect topComplication;

        // The coordinates of the large bottom complication
        private static final float BOTTOM_LARGE_COMPLICATION_TOP = 0.77f;
        private static final float BOTTOM_LARGE_COMPLICATION_BOTTOM = 0.92f;
        private Rect bottomLargeComplication;

        // The coordinates for the smaller bottom complications
        private static final float BOTTOM_COMPLICATIONS_TOP = 0.56f;
        private static final float BOTTOM_COMPLICATIONS_BOTTOM = 0.75f;
        private static final float BOTTOM_MIDDLE_COMPLICATION_LEFT = 0.405f;
        private static final float BOTTOM_MIDDLE_COMPLICATION_RIGHT = 0.595f;
        private static final float BOTTOM_LEFT_COMPLICATION_LEFT = 0.185f;
        private static final float BOTTOM_LEFT_COMPLICATION_RIGHT = 0.375f;
        private static final float BOTTOM_RIGHT_COMPLICATION_LEFT = 0.625f;
        private static final float BOTTOM_RIGHT_COMPLICATION_RIGHT = 0.815f;
        private Rect leftComplication;
        private Rect middleComplication;
        private Rect rightComplication;

        private final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        public void onCreate(SurfaceHolder holder) {
            Log.d(TAG, "Initializing engine");
            super.onCreate(holder);

            filter.addAction(Intent.ACTION_LOCALE_CHANGED);

            background.setColor(Color.BLACK);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFace.this).setAccentColor(getColor(R.color.date)).setAcceptsTapEvents(true).build());

            timePaint.setColor(getResources().getColor(R.color.time, getTheme()));
            timePaint.setTypeface(Typeface.DEFAULT_BOLD);
            timePaint.setAntiAlias(true);

            timePaintAmbient.set(timePaint);

            secondsPaint.setColor(getResources().getColor(R.color.seconds, getTheme()));
            secondsPaint.setTypeface(Typeface.DEFAULT);
            secondsPaint.setAntiAlias(true);

            datePaint.setColor(getResources().getColor(R.color.date, getTheme()));
            datePaint.setTypeface(Typeface.DEFAULT);
            datePaint.setAntiAlias(true);

            datePaintAmbient.set(datePaint);
            datePaintAmbient.setColor(Color.WHITE);

            initializeComplications();
        }

        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            screenHeight = height;
            screenWidth = width;

            Log.d(TAG, "Surface changed");
            int smaller = Math.min(width, height);
            float timeSize = DEFAULT_TIME_SIZE * smaller;
            timePaint.setTextSize(timeSize);
            timePaintAmbient.setTextSize(timeSize);
            secondsPaint.setTextSize(DEFAULT_SECONDS_SIZE * smaller);
            float timeLength = timePaint.measureText("12:34");
            timeX = (float) (width / 2) - (timeLength / 2);
            timeY = height * DEFAULT_TIME_Y;
            dateY = (float) (height / 2) + DEFAULT_DATE_VERTICAL_OFFSET * height;
            secondsX = timeLength + timeX;
            float dateSize = DEFAULT_DATE_SIZE * width;
            datePaint.setTextSize(dateSize);
            datePaintAmbient.setTextSize(dateSize);

            Rect backgroundComplication = new Rect(0, 0, width, height);
            complicationDrawables[BACKGROUND_COMPLICATION].setBounds(backgroundComplication);

            Log.d(TAG, "Calculating complication bounds");
            topComplication = new Rect(Math.round(LARGE_COMPLICATION_LEFT * width), Math.round(TOP_COMPLICATION_TOP * height), Math.round(LARGE_COMPLICATION_RIGHT * width), Math.round(TOP_COMPLICATION_BOTTOM * height));
            complicationDrawables[TOP_COMPLICATION].setBounds(topComplication);

            bottomLargeComplication = new Rect(Math.round(LARGE_COMPLICATION_LEFT * width), Math.round(BOTTOM_LARGE_COMPLICATION_TOP * height), Math.round(LARGE_COMPLICATION_RIGHT * width), Math.round(BOTTOM_LARGE_COMPLICATION_BOTTOM * height));
            complicationDrawables[BOTTOM_LARGE_COMPLICATION].setBounds(bottomLargeComplication);

            middleComplication = new Rect(Math.round(BOTTOM_MIDDLE_COMPLICATION_LEFT * smaller), Math.round(BOTTOM_COMPLICATIONS_TOP * smaller), Math.round(BOTTOM_MIDDLE_COMPLICATION_RIGHT * smaller), Math.round(BOTTOM_COMPLICATIONS_BOTTOM * smaller));
            complicationDrawables[BOTTOM_MIDDLE_COMPLICATION].setBounds(middleComplication);

            leftComplication = new Rect(Math.round(BOTTOM_LEFT_COMPLICATION_LEFT * smaller), Math.round(BOTTOM_COMPLICATIONS_TOP * smaller), Math.round(BOTTOM_LEFT_COMPLICATION_RIGHT * smaller), Math.round(BOTTOM_COMPLICATIONS_BOTTOM * smaller));
            complicationDrawables[BOTTOM_LEFT_COMPLICATION].setBounds(leftComplication);

            rightComplication = new Rect(Math.round(BOTTOM_RIGHT_COMPLICATION_LEFT * smaller), Math.round(BOTTOM_COMPLICATIONS_TOP * smaller), Math.round(BOTTOM_RIGHT_COMPLICATION_RIGHT * smaller), Math.round(BOTTOM_COMPLICATIONS_BOTTOM * smaller));
            complicationDrawables[BOTTOM_RIGHT_COMPLICATION].setBounds(rightComplication);

            if (!requiredBurnInProtection) {
                Log.d(TAG, "Clearing set Rectangles because they're not needed without burn in protection");
                topComplication = null;
                bottomLargeComplication = null;
                leftComplication = null;
                middleComplication = null;
                rightComplication = null;
            }
        }

        public void onPropertiesChanged (Bundle properties) {
            super.onPropertiesChanged(properties);

            requiredBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            // Clear complication bounds when burn in protection isn't required but they are set
            if (!requiredBurnInProtection && (topComplication != null || leftComplication != null || middleComplication != null || rightComplication != null)) {
                restoreCoordinates();
                topComplication = null;
                bottomLargeComplication = null;
                leftComplication = null;
                middleComplication = null;
                rightComplication = null;
                // Get complication bounds because they're needed for burn in protection
            } else if (requiredBurnInProtection && (topComplication == null || leftComplication == null || middleComplication == null || rightComplication == null)) {
                topComplication = complicationDrawables[TOP_COMPLICATION].getBounds();
                bottomLargeComplication = complicationDrawables[BOTTOM_LARGE_COMPLICATION].getBounds();
                leftComplication = complicationDrawables[BOTTOM_LEFT_COMPLICATION].getBounds();
                middleComplication = complicationDrawables[BOTTOM_MIDDLE_COMPLICATION].getBounds();
                rightComplication = complicationDrawables[BOTTOM_RIGHT_COMPLICATION].getBounds();
            }

            // Whether this device uses low bit ambient mode
            boolean lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            // Enable or disable anti alias for time and date depending on whether in low bit ambient
            if (lowBitAmbient) {
                timePaintAmbient.setAntiAlias(false);
                datePaintAmbient.setAntiAlias(false);
            } else {
                timePaintAmbient.setAntiAlias(true);
                datePaintAmbient.setAntiAlias(true);
            }
            for (ComplicationDrawable drawable : complicationDrawables) {
                drawable.setLowBitAmbient(lowBitAmbient);
            }
            Log.d(TAG, "Properties changed. Burn in: " + requiredBurnInProtection + " Low Bit Ambient: " + lowBitAmbient);
        }

        public void onTimeTick() {
            Log.d(TAG, "System Tick");
            super.onTimeTick();
            invalidate();
            updateTimer();
        }

        public void onAmbientModeChanged(boolean inAmbientMode) {
            Log.d(TAG, "Ambient mode " + (inAmbientMode ? "enabled" : "disabled"));
            super.onAmbientModeChanged(inAmbientMode);
            if (requiredBurnInProtection && !inAmbientMode) {
                restoreCoordinates();
            }
            for (ComplicationDrawable drawable : complicationDrawables) {
                drawable.setInAmbientMode(inAmbientMode);
            }
            invalidate();
            updateTimer();
        }

        public void onDraw(Canvas canvas, Rect bounds) {
            // Get basic data to draw
            long now = System.currentTimeMillis();
            calendar.setTimeInMillis(now);
            String time = createTime();
            String date = createDate();
            // Re-calculate the position of the date if it has changed since the last draw
            if(!date.equals(lastDate)) {
                String day = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) + " ";
                float lengthWODash = datePaint.measureText(day);
                float lengthWDash = datePaint.measureText(day + "|");
                float average = (lengthWDash + lengthWODash) / 2;
                dateX = (float) (bounds.right / 2) - average;
                lastDate = date;
            }
            // Draw the background complication if not in ambient and one is set
            if (!isInAmbientMode() && validBackground) {
                Log.d(TAG, "Drawing background complication");
                complicationDrawables[BACKGROUND_COMPLICATION].draw(canvas, now);
            } else {
                // Clear the background
                Log.d(TAG, "Clearing background");
                canvas.drawRect(bounds, background);
            }
            // Draw default ambient
            if (isInAmbientMode() && !requiredBurnInProtection) {
                Log.d(TAG, "Drawing ambient, no burn in protection");
                canvas.drawText(time, timeX, timeY, timePaintAmbient);
                canvas.drawText(date, dateX, dateY, datePaintAmbient);
                // Draw ambient with shifting coordinates
            } else if (isInAmbientMode()) {
                Log.d(TAG, "Drawing ambient, with burn in protection");
                randomizeCoordinates();
                canvas.drawText(time, timeXBurnIn, timeY, timePaintAmbient);
                canvas.drawText(date, dateXBurnIn, dateY, datePaintAmbient);
            } else {
                // Draw active
                Log.d(TAG, "Drawing active");
                String second = formatLeadingZeroes(calendar.get(Calendar.SECOND));
                canvas.drawText(time, timeX, timeY, timePaint);
                canvas.drawText(date, dateX, dateY, datePaint);
                canvas.drawText(second, secondsX, timeY, secondsPaint);
            }
            // Draw complications
            if (!isInAmbientMode() || complicationsInAmbient) {
                drawComplications(canvas, now);
            }
        }

        public void onVisibilityChanged(boolean visible) {
            Log.d(TAG, "Visibility changed: " + (visible ? "visible" : "invisible"));
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                calendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
            }
        }

        public void onComplicationDataUpdate(int complicationID, ComplicationData data) {
            complicationDrawables[complicationID].setComplicationData(data);
            complicationData[complicationID] = data;
            if (complicationID == BACKGROUND_COMPLICATION) {
                validBackground = data.getType() == ComplicationData.TYPE_LARGE_IMAGE;
            }
        }

        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Log.d(TAG, "Tap registered");
            if (tapType == TAP_TYPE_TAP) {
                byte id = getTappedComplicationsID(x, y);
                if (id != -1) {
                    PendingIntent action = complicationData[id].getTapAction();
                    if (action != null) {
                        try {
                            action.send();
                        } catch (PendingIntent.CanceledException e) {
                            Log.e(TAG, "Sending intent for tap action failed", e);
                        }
                    } else if (complicationData[id].getType() == ComplicationData.TYPE_NO_PERMISSION) {
                        // Watch face does not have permission to receive complication data, so launch
                        // permission request.
                        ComponentName componentName = new ComponentName(
                                getApplicationContext(),
                                WatchFace.class);

                        Intent permissionRequestIntent =
                                ComplicationHelperActivity.createPermissionRequestHelperIntent(
                                        getApplicationContext(), componentName);

                        startActivity(permissionRequestIntent);
                    } else {
                        Log.i(TAG, "Complication didn't provide an intent");
                    }
                }
            }
        }

        /**
         * Creates a screenshot of how the watch currently looks
         * Gets used for showing a screenshot in the settings menu
         * @return How the watch currently looks
         * @throws IllegalStateException Gets thrown when the engine isn't fully initialized and therefore cannot produce a valid screenshot
         */
        protected Bitmap screenshot() throws IllegalStateException {
            Log.d(TAG, "Screenshot requested");
            if (screenWidth == null || screenHeight == null) {
                Log.d(TAG, "Screen size required for screenshot not available");
                throw new IllegalStateException("WatchFace not fully initialized");
            }
            Bitmap bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Rect bounds = new Rect(0, 0, screenWidth, screenHeight);
            this.onDraw(canvas, bounds);
            return bitmap;
        }

        /**
         * Creates an array telling which complications are set and which aren't
         * Gets used by the settings menu to determine which icon gets shown in that place
         * @return Which complications are set
         */
        protected boolean[] complicationLocations() {
            boolean[] rect = new boolean[complicationData.length - 1];
            for (byte i = 1; i < complicationData.length; i++) {
                rect[i - 1] = complicationData[i] != null && complicationData[i].getType() != ComplicationData.TYPE_NOT_CONFIGURED && complicationData[i].getType() != ComplicationData.TYPE_EMPTY;
            }
            return rect;
        }

        /**
         * Offsets the complications and date and time by a random offset
         * The complications get directly offset
         * For date and time it is necessary to use the corresponding burn in variable,
         * the originals don't get changed
         */
        private void randomizeCoordinates() {
            int offset = (int) Math.round(Math.random() * 10);
            Log.d(TAG, "Randomizing coordinates by " + offset);
            if (lastMovedRight) {
                offset = -offset;
            }
            Rect topComplication = new Rect(this.topComplication);
            Rect bottomLargeComplication = new Rect(this.bottomLargeComplication);
            Rect leftComplication = new Rect(this.leftComplication);
            Rect middleComplication = new Rect(this.middleComplication);
            Rect rightComplication = new Rect(this.rightComplication);
            topComplication.offset(offset, 0);
            bottomLargeComplication.offset(offset, 0);
            leftComplication.offset(offset, 0);
            middleComplication.offset(offset, 0);
            rightComplication.offset(offset, 0);
            complicationDrawables[TOP_COMPLICATION].setBounds(topComplication);
            complicationDrawables[BOTTOM_LARGE_COMPLICATION].setBounds(bottomLargeComplication);
            complicationDrawables[BOTTOM_LEFT_COMPLICATION].setBounds(leftComplication);
            complicationDrawables[BOTTOM_MIDDLE_COMPLICATION].setBounds(middleComplication);
            complicationDrawables[BOTTOM_RIGHT_COMPLICATION].setBounds(rightComplication);
            lastMovedRight = !lastMovedRight;

            timeXBurnIn = timeX + offset;
            dateXBurnIn = dateX + offset;
        }

        /**
         * Restores the original coordinates for complications
         * For date and time, simply don't use the offset variables
         */
        private void restoreCoordinates() {
            Log.d(TAG, "restoring Coordinates");
            complicationDrawables[TOP_COMPLICATION].setBounds(topComplication);
            complicationDrawables[BOTTOM_LARGE_COMPLICATION].setBounds(bottomLargeComplication);
            complicationDrawables[BOTTOM_LEFT_COMPLICATION].setBounds(leftComplication);
            complicationDrawables[BOTTOM_MIDDLE_COMPLICATION].setBounds(middleComplication);
            complicationDrawables[BOTTOM_RIGHT_COMPLICATION].setBounds(rightComplication);
        }

        /**
         * Creates a string of the current time in 24h format
         * Because fuck AM/PM
         * @return A string representing the time
         */
        private String createTime() {
            String hour = formatLeadingZeroes(calendar.get(Calendar.HOUR_OF_DAY));
            String minute = formatLeadingZeroes(calendar.get(Calendar.MINUTE));
            return hour + ":" + minute;
        }

        /**
         * Creates the string to be shown as date consisting of the weekday, a stroke and the actual date
         * @return A string of the current day and date
         */
        private String createDate() {
            String day = formatLeadingZeroes(calendar.get(Calendar.DAY_OF_MONTH));
            String month = formatLeadingZeroes(calendar.get(Calendar.MONTH) + 1);
            String year = String.valueOf(calendar.get(Calendar.YEAR));
            String dayName = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
            return dayName + " | " + day + "/" + month + "/" + year;
        }

        /**
         * Creates a string of a numerical value with 2 leading zeroes
         * @param numbers The value to format
         * @return A string formatted to 2 leading zeroes
         */
        private String formatLeadingZeroes(long numbers) {
            return String.format("%02d", numbers);
        }

        /**
         * Register this engine to receive timezone updates
         */
        private void registerReceiver() {
            if (receiving) {
                return;
            }
            WatchFace.this.registerReceiver(timeZoneReceiver, filter);
            receiving = true;
        }

        /**
         * Unregister this engine from receiving timezone updates
         */
        private void unregisterReceiver() {
            if (!receiving) {
                return;
            }
            WatchFace.this.unregisterReceiver(timeZoneReceiver);
            receiving = false;
        }

        /**
         * Change whether this engine gets updated every second or only listens for the system tick every minute
         */
        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_DISPLAY);
            if (shouldTimerRun()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_DISPLAY);
            }
        }

        private boolean shouldTimerRun() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Creates the complications and the array they get stored in
         */
        private void initializeComplications() {
            Log.d(TAG, "Initializing complications");

            complicationDrawables[BACKGROUND_COMPLICATION] = (ComplicationDrawable) getDrawable(R.drawable.background_complication);
            complicationDrawables[BACKGROUND_COMPLICATION].setContext(getApplicationContext());

            for (byte i = 1; i < complicationDrawables.length; i++) {
                complicationDrawables[i] = (ComplicationDrawable) getDrawable(R.drawable.complication);
                complicationDrawables[i].setContext(getApplicationContext());
            }

            setActiveComplications(BACKGROUND_COMPLICATION, TOP_COMPLICATION, BOTTOM_LARGE_COMPLICATION, BOTTOM_LEFT_COMPLICATION, BOTTOM_MIDDLE_COMPLICATION, BOTTOM_RIGHT_COMPLICATION);
        }

        /**
         * Draws the foreground complications on the provided canvas
         * @param canvas The canvas the complications should be drawn on
         * @param time The time the complications should use when drawing
         */
        private void drawComplications(Canvas canvas, long time) {
            for (byte i = 1; i < complicationDrawables.length; i++) {
                complicationDrawables[i].draw(canvas, time);
            }
        }

        /**
         * Finds out which complication the used tapped on
         * Ignores the background complication
         * @param x The x coordinate of the tapped location
         * @param y The y coordinate of the tapped location
         * @return The ID of the tapped location or -1 if tapped on empty space
         */
        private byte getTappedComplicationsID(int x, int y) {
            Log.d(TAG, "Finding complication at " + x + ";" + y);
            for (byte i = 1; i < complicationDrawables.length; i++) {
                ComplicationDrawable drawable = complicationDrawables[i];
                if (drawable.getBounds().contains(x, y)) {
                    return i;
                }
            }
            Log.d(TAG, "No complication found");
            return -1;
        }
    }

    private static class UpdateTimeHandler extends Handler {

        private static final String TAG = "WatchFace.UpdateTimeHandler";

        private final WeakReference<Engine> engineReference;

        UpdateTimeHandler(WeakReference<Engine> engine) {
            this.engineReference = engine;
        }

        @Override
        public void handleMessage(Message message) {
            Engine engine = engineReference.get();
            if (engine != null) {
                if (message.what == MSG_UPDATE_DISPLAY) {
                    Log.d(TAG, "received time update message");
                    engine.invalidate();
                    if (engine.shouldTimerRun()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        sendEmptyMessageDelayed(MSG_UPDATE_DISPLAY, delayMs);
                    }
                }
            }
        }
    }
}