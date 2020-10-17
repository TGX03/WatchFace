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
    private static final byte BACKGROUND_COMPLICATION = 0;
    private static final byte TOP_COMPLICATION = 1;
    private static final byte BOTTOM_LEFT_COMPLICATION = 2;
    private static final byte BOTTOM_MIDDLE_COMPLICATION = 3;
    private static final byte BOTTOM_RIGHT_COMPLICATION = 4;

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

    private ComplicationDrawable[] complicationDrawables;
    private ComplicationData[] complicationData;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final byte MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        engine = new Engine();
        return engine;
    }

    protected static Engine getEngine() {
        if (engine != null) {
            return engine;
        } else {
            throw new IllegalStateException("Currently no engine available");
        }
    }

    protected static byte getComplicationID(ComplicationID id) {
        switch (id) {
            case BACKGROUND:
                return BACKGROUND_COMPLICATION;
            case TOP:
                return TOP_COMPLICATION;
            case BOTTOM_LEFT:
                return BOTTOM_LEFT_COMPLICATION;
            case BOTTOM_MIDDLE:
                return BOTTOM_MIDDLE_COMPLICATION;
            case BOTTOM_RIGHT:
                return BOTTOM_RIGHT_COMPLICATION;
            default:
                return -1;
        }
    }

    protected static int[] getSupportedComplications(ComplicationID id) {
        switch (id) {
            case BACKGROUND:
                return COMPLICATION_SUPPORTED_TYPES[0];
            case TOP:
                return COMPLICATION_SUPPORTED_TYPES[1];
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT:
            case BOTTOM_MIDDLE:
                return COMPLICATION_SUPPORTED_TYPES[2];
            default:
                return new int[0];
        }
    }

    protected static void setComplicationsInAmbient(boolean enabled) {
        Log.d(TAG, "Complications in ambient " + (enabled ? "enabled" : "disabled"));
        complicationsInAmbient = enabled;
    }

    protected static boolean getComplicationsInAmbient() {
        return complicationsInAmbient;
    }

    protected class Engine extends CanvasWatchFaceService.Engine {

        private static final String TAG = "WatchFace.Engine";

        // The device features
        private Boolean requiredBurnInProtection;
        private boolean lastMovedRight = false;

        // Whether this is currently registered for receiving timezone changes
        private boolean receiving;
        // The filter for receiving timezone changes
        private final IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);

        private Integer screenWidth;
        private Integer screenHeight;

        // Values for time position and size
        private float timeX;
        private float timeY;
        private Float timeXBurnIn;
        private static final float DEFAULT_TIME_SIZE = 0.3f;
        private static final float DEFAULT_SECONDS_SIZE = 0.09f;

        // Values for seconds position and size
        private float secondsX;

        // Values for date position and size
        private String lastDate;
        private float dateX;
        private float dateY;
        private Float dateXBurnIn;
        private static final float DEFAULT_DATE_VERTICAL_OFFSET = 0.09f;
        private static final float DEFAULT_DATE_SIZE = 0.07f;

        // Styles for elements
        private Paint background;
        private Paint timePaint;
        private Paint timePaintAmbient;
        private Paint datePaint;
        private Paint datePaintAmbient;
        private Paint secondsPaint;

        private final Calendar calendar = Calendar.getInstance();

        private final Handler updateTimeHandler = new UpdateTimeHandler(new WeakReference<>(this));

        private boolean validBackground = false;

        // The coordinates for the large top complication
        private static final float TOP_COMPLICATION_LEFT = 0.2f;
        private static final float TOP_COMPLICATION_TOP = 0.1f;
        private static final float TOP_COMPLICATION_RIGHT = 0.8f;
        private static final float TOP_COMPLICATION_BOTTOM = 0.25f;
        private Rect topComplication;

        // The coordinates for the smaller bottom complications
        private static final float BOTTOM_COMPLICATIONS_TOP = 0.65f;
        private static final float BOTTOM_COMPLICATIONS_BOTTOM = 0.85f;
        private static final float BOTTOM_MIDDLE_COMPLICATION_LEFT = 0.4f;
        private static final float BOTTOM_MIDDLE_COMPLICATION_RIGHT = 0.6f;
        private static final float BOTTOM_LEFT_COMPLICATION_LEFT = 0.17f;
        private static final float BOTTOM_LEFT_COMPLICATION_RIGHT = 0.37f;
        private static final float BOTTOM_RIGHT_COMPLICATION_LEFT = 0.63f;
        private static final float BOTTOM_RIGHT_COMPLICATION_RIGHT = 0.83f;
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

            background = new Paint();
            background.setColor(Color.BLACK);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFace.this).setAccentColor(getColor(R.color.date)).setAcceptsTapEvents(true).build());
            timePaint = new Paint();
            timePaint.setColor(getResources().getColor(R.color.time, getTheme()));
            timePaint.setTypeface(Typeface.DEFAULT_BOLD);
            timePaint.setAntiAlias(true);

            timePaintAmbient = new Paint(timePaint);

            secondsPaint = new Paint();
            secondsPaint.setColor(getResources().getColor(R.color.seconds, getTheme()));
            secondsPaint.setTypeface(Typeface.DEFAULT);
            secondsPaint.setAntiAlias(true);

            datePaint = new Paint();
            datePaint.setColor(getResources().getColor(R.color.date, getTheme()));
            datePaint.setTypeface(Typeface.DEFAULT);
            datePaint.setAntiAlias(true);

            datePaintAmbient = new Paint(datePaint);
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
            timeY = (float) height / 2;
            dateY = (float) (height / 2) + DEFAULT_DATE_VERTICAL_OFFSET * height;
            secondsX = timeLength + timeX;
            float dateSize = DEFAULT_DATE_SIZE * width;
            datePaint.setTextSize(dateSize);
            datePaintAmbient.setTextSize(dateSize);

            Rect backgroundComplication = new Rect(0, 0, width, height);
            complicationDrawables[BACKGROUND_COMPLICATION].setBounds(backgroundComplication);

            topComplication = new Rect(Math.round(TOP_COMPLICATION_LEFT * width), Math.round(TOP_COMPLICATION_TOP * height), Math.round(TOP_COMPLICATION_RIGHT * width), Math.round(TOP_COMPLICATION_BOTTOM * height));
            complicationDrawables[TOP_COMPLICATION].setBounds(topComplication);

            middleComplication = new Rect(Math.round(BOTTOM_MIDDLE_COMPLICATION_LEFT * smaller), Math.round(BOTTOM_COMPLICATIONS_TOP * smaller), Math.round(BOTTOM_MIDDLE_COMPLICATION_RIGHT * smaller), Math.round(BOTTOM_COMPLICATIONS_BOTTOM * smaller));
            complicationDrawables[BOTTOM_MIDDLE_COMPLICATION].setBounds(middleComplication);

            leftComplication = new Rect(Math.round(BOTTOM_LEFT_COMPLICATION_LEFT * smaller), Math.round(BOTTOM_COMPLICATIONS_TOP * smaller), Math.round(BOTTOM_LEFT_COMPLICATION_RIGHT * smaller), Math.round(BOTTOM_COMPLICATIONS_BOTTOM * smaller));
            complicationDrawables[BOTTOM_LEFT_COMPLICATION].setBounds(leftComplication);

            rightComplication = new Rect(Math.round(BOTTOM_RIGHT_COMPLICATION_LEFT * smaller), Math.round(BOTTOM_COMPLICATIONS_TOP * smaller), Math.round(BOTTOM_RIGHT_COMPLICATION_RIGHT * smaller), Math.round(BOTTOM_COMPLICATIONS_BOTTOM * smaller));
            complicationDrawables[BOTTOM_RIGHT_COMPLICATION].setBounds(rightComplication);

            if (requiredBurnInProtection != null && !requiredBurnInProtection) {
                Log.d(TAG, "Clearing set Rectangles");
                topComplication = null;
                leftComplication = null;
                middleComplication = null;
                rightComplication = null;
            }
        }

        public void onPropertiesChanged (Bundle properties) {
            super.onPropertiesChanged(properties);

            requiredBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            if (!requiredBurnInProtection) {
                if (dateXBurnIn != null || timeXBurnIn != null) {
                    restoreCoordinates();
                }
                topComplication = null;
                leftComplication = null;
                middleComplication = null;
                rightComplication = null;
            } else if (topComplication == null || leftComplication == null || middleComplication == null || rightComplication == null) {
                topComplication = complicationDrawables[TOP_COMPLICATION].getBounds();
                leftComplication = complicationDrawables[BOTTOM_LEFT_COMPLICATION].getBounds();
                middleComplication = complicationDrawables[BOTTOM_MIDDLE_COMPLICATION].getBounds();
                rightComplication = complicationDrawables[BOTTOM_RIGHT_COMPLICATION].getBounds();
            }

            // The device features
            boolean lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            if (lowBitAmbient) {
                timePaintAmbient.setAntiAlias(false);
                datePaintAmbient.setAntiAlias(false);
            }
            for (ComplicationDrawable drawable : complicationDrawables) {
                drawable.setLowBitAmbient(lowBitAmbient);
            }
            Log.d(TAG, "Properties changed. Burn in: " + requiredBurnInProtection.toString() + " Low Bit Ambient: " + lowBitAmbient);
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
            for (ComplicationDrawable drawable : complicationDrawables) {
                drawable.setInAmbientMode(inAmbientMode);
            }
            invalidate();
            updateTimer();
        }

        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            calendar.setTimeInMillis(now);
            String time = createTime();
            String date = createDate();
            if(!date.equals(lastDate)) {
                String day = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) + " ";
                float lengthWODash = datePaint.measureText(day);
                float lengthWDash = datePaint.measureText(day + "|");
                float average = (lengthWDash + lengthWODash) / 2;
                dateX = (float) (bounds.right / 2) - average;
                lastDate = date;
            }
            canvas.drawRect(bounds, background);
            if (!isInAmbientMode() && validBackground) {
                Log.d(TAG, "Drawing background complication");
                complicationDrawables[BACKGROUND_COMPLICATION].draw(canvas, now);
            }
            if (isInAmbientMode() && !requiredBurnInProtection) {
                Log.d(TAG, "Drawing ambient, no burn in protection");
                canvas.drawText(time, timeX, timeY, timePaintAmbient);
                canvas.drawText(date, dateX, dateY, datePaintAmbient);
            } else if (isInAmbientMode()) {
                Log.d(TAG, "Drawing ambient, with burn in protection");
                randomizeCoordinates();
                canvas.drawText(time, timeXBurnIn, timeY, timePaintAmbient);
                canvas.drawText(date, dateXBurnIn, dateY, datePaintAmbient);
            } else {
                Log.d(TAG, "Drawing active");
                String second = formatLeadingZeroes(calendar.get(Calendar.SECOND));
                canvas.drawText(time, timeX, timeY, timePaint);
                canvas.drawText(date, dateX, dateY, datePaint);
                canvas.drawText(second, secondsX, timeY, secondsPaint);
            }
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

        protected Bitmap screenshot() {
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

        protected boolean[] complicationLocations() {
            boolean[] rect = new boolean[complicationData.length - 1];
            for (byte i = 1; i < complicationData.length; i++) {
                rect[i - 1] = complicationData[i] != null && complicationData[i].getType() != ComplicationData.TYPE_NOT_CONFIGURED && complicationData[i].getType() != ComplicationData.TYPE_EMPTY;
            }
            return rect;
        }

        private void randomizeCoordinates() {
            Log.d(TAG, "Randomizing coordinates");
            int offset = (int) Math.round(Math.random() * 10);
            if (lastMovedRight) {
                offset = -offset;
            }
            Rect topComplication = new Rect(this.topComplication);
            Rect leftComplication = new Rect(this.leftComplication);
            Rect middleComplication = new Rect(this.middleComplication);
            Rect rightComplication = new Rect(this.rightComplication);
            topComplication.offset(offset, 0);
            leftComplication.offset(offset, 0);
            middleComplication.offset(offset, 0);
            rightComplication.offset(offset, 0);
            complicationDrawables[TOP_COMPLICATION].setBounds(topComplication);
            complicationDrawables[BOTTOM_LEFT_COMPLICATION].setBounds(leftComplication);
            complicationDrawables[BOTTOM_MIDDLE_COMPLICATION].setBounds(middleComplication);
            complicationDrawables[BOTTOM_RIGHT_COMPLICATION].setBounds(rightComplication);
            lastMovedRight = !lastMovedRight;

            timeXBurnIn = timeX + offset;
            dateXBurnIn = dateX + offset;
        }

        private void restoreCoordinates() {
            Log.d(TAG, "restoring Coordinates");
            complicationDrawables[TOP_COMPLICATION].setBounds(topComplication);
            complicationDrawables[BOTTOM_LEFT_COMPLICATION].setBounds(leftComplication);
            complicationDrawables[BOTTOM_MIDDLE_COMPLICATION].setBounds(middleComplication);
            complicationDrawables[BOTTOM_RIGHT_COMPLICATION].setBounds(rightComplication);
        }

        private String createTime() {
            String hour = formatLeadingZeroes(calendar.get(Calendar.HOUR_OF_DAY));
            String minute = formatLeadingZeroes(calendar.get(Calendar.MINUTE));
            return hour + ":" + minute;
        }

        private String createDate() {
            String day = formatLeadingZeroes(calendar.get(Calendar.DAY_OF_MONTH));
            String month = formatLeadingZeroes(calendar.get(Calendar.MONTH) + 1);
            String year = String.valueOf(calendar.get(Calendar.YEAR));
            String dayName = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
            return dayName + " | " + day + "/" + month + "/" + year;
        }

        private String formatLeadingZeroes(long numbers) {
            return String.format("%02d", numbers);
        }

        private void registerReceiver() {
            if (receiving) {
                return;
            }
            WatchFace.this.registerReceiver(timeZoneReceiver, filter);
            receiving = true;
        }

        private void unregisterReceiver() {
            if (!receiving) {
                return;
            }
            WatchFace.this.unregisterReceiver(timeZoneReceiver);
            receiving = false;
        }

        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerRun()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private boolean shouldTimerRun() {
            return isVisible() && !isInAmbientMode();
        }

        private void initializeComplications() {
            Log.d(TAG, "Initializing complications");
            complicationDrawables = new ComplicationDrawable[5];

            ComplicationDrawable background = (ComplicationDrawable) getDrawable(R.drawable.background_complication);
            background.setContext(getApplicationContext());
            complicationDrawables[BACKGROUND_COMPLICATION] = background;

            ComplicationDrawable top = (ComplicationDrawable) getDrawable(R.drawable.complication);
            top.setContext(getApplicationContext());
            complicationDrawables[TOP_COMPLICATION] = top;

            ComplicationDrawable left = (ComplicationDrawable) getDrawable(R.drawable.complication);
            ComplicationDrawable middle = (ComplicationDrawable) getDrawable(R.drawable.complication);
            ComplicationDrawable right = (ComplicationDrawable) getDrawable(R.drawable.complication);

            left.setContext(getApplicationContext());
            middle.setContext(getApplicationContext());
            right.setContext(getApplicationContext());

            complicationDrawables[BOTTOM_LEFT_COMPLICATION] = left;
            complicationDrawables[BOTTOM_MIDDLE_COMPLICATION] = middle;
            complicationDrawables[BOTTOM_RIGHT_COMPLICATION] = right;

            complicationData = new ComplicationData[5];

            setActiveComplications(BACKGROUND_COMPLICATION, TOP_COMPLICATION, BOTTOM_LEFT_COMPLICATION, BOTTOM_MIDDLE_COMPLICATION, BOTTOM_RIGHT_COMPLICATION);
        }

        private void drawComplications(Canvas canvas, long time) {
            for (byte i = 1; i < 5; i++) {
                complicationDrawables[i].draw(canvas, time);
            }
        }

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
                if (message.what == MSG_UPDATE_TIME) {
                    Log.d(TAG, "received time update message");
                    engine.invalidate();
                    if (engine.shouldTimerRun()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                    }
                }
            }
        }
    }
}