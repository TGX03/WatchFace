package de.tgx03.watchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.SparseArray;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class WatchFace extends CanvasWatchFaceService {

    // Updates rate in milliseconds for interactive mode
    private static final short INTERACTIVE_UPDATE_RATE_MS = 1000;

    // Complication IDs
    private static final byte BACKGROUND_COMPLICATION = 0;
    private static final byte TOP_COMPLICATION = 1;

    private static final byte[] COMPLICATION_IDS = {BACKGROUND_COMPLICATION, TOP_COMPLICATION};

    private static final int[][] COMPLICATION_SUPPORTED_TYPES = {{ComplicationData.TYPE_LARGE_IMAGE},
            {ComplicationData.TYPE_LONG_TEXT}};

    private SparseArray<ComplicationData> complicationData;
    private SparseArray<ComplicationDrawable> complicationDrawables;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final byte MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    protected static byte getComplicationID(ComplicationID id) {
        switch (id) {
            case BACKGROUND:
                return BACKGROUND_COMPLICATION;
            case TOP:
                return TOP_COMPLICATION;
            default:
                return -1;
        }
    }

    protected static byte[] getComplicationIDs() {
        return COMPLICATION_IDS;
    }

    protected static int[] getSupportedComplications(ComplicationID id) {
        switch (id) {
            case BACKGROUND:
                return COMPLICATION_SUPPORTED_TYPES[0];
            case TOP:
                return COMPLICATION_SUPPORTED_TYPES[1];
            default:
                return new int[0];
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        // The device features
        private boolean lowBitAmbient;
        private boolean requiredBurnInProtection;

        // Whether this is currently registered for receiving timezone changes
        private boolean receiving;
        // The filter for receiving timezone changes
        private final IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);

        // Values for time position and size
        private float timeX;
        private float timeY;
        private static final float DEFAULT_TIME_SIZE = 0.3f;

        // Values for seconds position and size
        private float secondsX;

        // Values for date position and size
        private String lastDate;
        private float dateX;
        private float dateY;
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

        private static final float TOP_COMPLICATION_LEFT = 0.2f;
        private static final float TOP_COMPLICATION_TOP = 0.125f;
        private static final float TOP_COMPLICATION_RIGHT = 0.8f;
        private static final float TOP_COMPLICATION_BOTTOM = 0.25f;

        private final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            filter.addAction(Intent.ACTION_LOCALE_CHANGED);

            background = new Paint();
            background.setColor(Color.BLACK);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFace.this).setAccentColor(Color.parseColor("#FBBC04")).build());
            timePaint = new Paint();
            timePaint.setColor(getResources().getColor(R.color.time, getTheme()));
            timePaint.setTypeface(Typeface.DEFAULT_BOLD);
            timePaint.setAntiAlias(true);

            timePaintAmbient = new Paint(timePaint);

            secondsPaint = new Paint();
            secondsPaint.setColor(getResources().getColor(R.color.seconds, getTheme()));
            secondsPaint.setTypeface(Typeface.DEFAULT);
            secondsPaint.setTextSize(30);
            secondsPaint.setAntiAlias(true);

            datePaint = new Paint();
            datePaint.setColor(getResources().getColor(R.color.date, getTheme()));
            datePaint.setTypeface(Typeface.DEFAULT_BOLD);
            datePaint.setAntiAlias(true);

            datePaintAmbient = new Paint(datePaint);
            datePaintAmbient.setColor(Color.WHITE);

            initializeComplications();
        }

        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            int smaller = Math.min(width, height);
            float timeSize = DEFAULT_TIME_SIZE * smaller;
            timePaint.setTextSize(timeSize);
            timePaintAmbient.setTextSize(timeSize);
            float timeLength = timePaint.measureText("12:34");
            timeX = (float) (width / 2) - (timeLength / 2);
            timeY = (float) height / 2;
            dateY = (float) (height / 2) + DEFAULT_DATE_VERTICAL_OFFSET * height;
            secondsX = timeLength + timeX;
            float dateSize = DEFAULT_DATE_SIZE * width;
            datePaint.setTextSize(dateSize);
            datePaintAmbient.setTextSize(dateSize);

            Rect backgroundComplication = new Rect(0, 0, width, height);
            complicationDrawables.get(BACKGROUND_COMPLICATION).setBounds(backgroundComplication);

            Rect topComplication = new Rect((int) (TOP_COMPLICATION_LEFT * width), (int) (TOP_COMPLICATION_TOP * height), (int) (TOP_COMPLICATION_RIGHT * width), (int) (TOP_COMPLICATION_BOTTOM * height));
            complicationDrawables.get(TOP_COMPLICATION).setBounds(topComplication);
        }

        public void onPropertiesChanged (Bundle properties) {
            super.onPropertiesChanged(properties);

            requiredBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            if (lowBitAmbient) {
                timePaintAmbient.setAntiAlias(false);
                datePaintAmbient.setAntiAlias(false);
            }
        }

        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
            updateTimer();
        }

        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            for (byte i = 0; i < COMPLICATION_IDS.length; i++) {
                complicationDrawables.get(i).setInAmbientMode(inAmbientMode);
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
                String day = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) + " |";
                float dayLength = datePaint.measureText(day);
                dateX = (float) (bounds.right / 2) - dayLength;
                lastDate = date;
            }
            canvas.drawRect(bounds, background);
            if (!isInAmbientMode()) {
                complicationDrawables.get(BACKGROUND_COMPLICATION).draw(canvas, now);
            }
            if (isInAmbientMode()) {
                canvas.drawText(time, timeX, timeY, timePaintAmbient);
                canvas.drawText(date, dateX, dateY, datePaintAmbient);
            } else {
                String second = formatLeadingZeroes(calendar.get(Calendar.SECOND));
                canvas.drawText(time, timeX, timeY, timePaint);
                canvas.drawText(date, dateX, dateY, datePaint);
                canvas.drawText(second, secondsX, timeY, secondsPaint);
            }
            drawComplications(canvas, now);
        }

        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                calendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
            }
        }

        public void onComplicationDataUpdate(int complicationID, ComplicationData data) {
            complicationData.put(complicationID, data);
            complicationDrawables.get(complicationID).setComplicationData(data);
        }

        private String createTime() {
            String hour = formatLeadingZeroes(calendar.get(Calendar.HOUR_OF_DAY));
            String minute = formatLeadingZeroes(calendar.get(Calendar.MINUTE));
            return hour + ":" + minute;
        }

        private String createDate() {
            String day = formatLeadingZeroes(calendar.get(Calendar.DAY_OF_MONTH));
            String month = formatLeadingZeroes(calendar.get(Calendar.MONTH));
            String year = String.valueOf(calendar.get(Calendar.YEAR));
            String dayName = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
            return dayName + " | " + day + "/" + month + "/" + year;
        }

        private String formatLeadingZeroes(String numbers) {
            return String.format("%02d", numbers);
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
            complicationData = new SparseArray<>(COMPLICATION_IDS.length);
            complicationDrawables = new SparseArray<>(COMPLICATION_IDS.length);

            ComplicationDrawable background = (ComplicationDrawable) getDrawable(R.drawable.background_complication);
            background.setContext(getApplicationContext());
            complicationDrawables.put(BACKGROUND_COMPLICATION, background);

            ComplicationDrawable top = (ComplicationDrawable) getDrawable(R.drawable.complication);
            top.setContext(getApplicationContext());
            complicationDrawables.put(TOP_COMPLICATION, top);

            setActiveComplications(BACKGROUND_COMPLICATION, TOP_COMPLICATION);
        }

        private void drawComplications(Canvas canvas, long time) {
            for (byte i = 1; i < COMPLICATION_IDS.length; i++) {
                complicationDrawables.get(i).draw(canvas, time);
            }
        }
    }

    private static class UpdateTimeHandler extends Handler {
        private final WeakReference<Engine> engineReference;

        UpdateTimeHandler(WeakReference<Engine> engine) {
            this.engineReference = engine;
        }

        @Override
        public void handleMessage(Message message) {
            Engine engine = engineReference.get();
            if (engine != null) {
                if (message.what == MSG_UPDATE_TIME) {
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