package de.tgx03.watchface;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;

import static de.tgx03.watchface.WatchFace.complicationsInAmbient;
import static de.tgx03.watchface.WatchFace.emptyComplications;

/**
 * A class representing the settings for the watchface
 * which are the complications and settings for when they should be drawn
 */
public class WatchFaceConfig extends Activity implements CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "WatchFaceConfig";

    private static final short COMPLICATION_CONFIG_REQUEST_CODE = 1001;

    private ComponentName watchFaceComponent;

    private Switch complicationsInAmbientSwitch;
    private Switch emptyComplicationsSwitch;

    protected void onCreate(Bundle savedInstance) {
        Log.d(TAG, "Creating config activity");

        super.onCreate(savedInstance);

        watchFaceComponent = new ComponentName(getApplicationContext(), WatchFace.class);

        setContentView(R.layout.settings_list);
        complicationsInAmbientSwitch = findViewById(R.id.ComplicationsInAmbient);
        complicationsInAmbientSwitch.setChecked(complicationsInAmbient);
        complicationsInAmbientSwitch.setOnCheckedChangeListener(this);

        emptyComplicationsSwitch = findViewById(R.id.empty_complications);
        emptyComplicationsSwitch.setChecked(emptyComplications);
        emptyComplicationsSwitch.setOnCheckedChangeListener(this);

        try {
            Log.d(TAG, "Getting watchface screenshot");
            Bitmap watchFace = WatchFace.getEngine().screenshot();
            ImageView image = findViewById(R.id.settingspreview);
            image.setImageBitmap(watchFace);
            boolean[] setComplications = WatchFace.getEngine().complicationLocations();
            Drawable complicationSet = getDrawable(R.drawable.added_complication);
            if (setComplications[0]) {
                ImageButton topButton = findViewById(R.id.SelectTopComplication);
                topButton.setImageDrawable(complicationSet);
            }
            if (setComplications[1]) {
                ImageButton bottomLargeButton = findViewById(R.id.SelectBottomLargeComplication);
                bottomLargeButton.setImageDrawable(complicationSet);
            }
            if (setComplications[2]) {
                ImageButton leftButton = findViewById(R.id.SelectLeftComplication);
                leftButton.setImageDrawable(complicationSet);
            }
            if (setComplications[3]) {
                ImageButton middleButton = findViewById(R.id.SelectMiddleComplication);
                middleButton.setImageDrawable(complicationSet);
            }
            if (setComplications[4]) {
                ImageButton rightButton = findViewById(R.id.SelectRightComplication);
                rightButton.setImageDrawable(complicationSet);
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "Couldn't get watchface screenshot", e);
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroyed config activity");
    }

    /**
     * Launches the dialog to select the background complication
     *
     * @param view The view calling this method
     */
    public void chooseBackground(View view) {
        Log.d(TAG, "Choosing background complication");
        launchComplicationHelperActivity(WatchFace.BACKGROUND_COMPLICATION);
    }

    /**
     * Launches the dialog to select the top complication
     *
     * @param view The view calling this method
     */
    public void chooseTopComplication(View view) {
        Log.d(TAG, "Choosing top complication");
        launchComplicationHelperActivity(WatchFace.TOP_COMPLICATION);
    }

    /**
     * Launches the dialog to select the bottom complication
     *
     * @param view The view calling this method
     */
    public void chooseBottomLargeComplication(View view) {
        Log.d(TAG, "Choosing large bottom complication");
        launchComplicationHelperActivity(WatchFace.BOTTOM_LARGE_COMPLICATION);
    }

    /**
     * Launches the dialog to select one of the three smaller complications
     *
     * @param view The view calling this method
     */
    public void chooseBottomComplication(View view) {
        Log.d(TAG, "Choosing a bottom complication");
        if (view.equals(findViewById(R.id.SelectMiddleComplication))) {
            launchComplicationHelperActivity(WatchFace.BOTTOM_MIDDLE_COMPLICATION);
        } else if (view.equals(findViewById(R.id.SelectLeftComplication))) {
            launchComplicationHelperActivity(WatchFace.BOTTOM_LEFT_COMPLICATION);
        } else if (view.equals(findViewById(R.id.SelectRightComplication))) {
            launchComplicationHelperActivity(WatchFace.BOTTOM_RIGHT_COMPLICATION);
        }
    }

    /**
     * Launches the system dialog to select a complication for a given complication id
     *
     * @param id The id of the complication to set
     */
    private void launchComplicationHelperActivity(byte id) {
        Log.d(TAG, "Launching complication chooser for complication " + id);

        if (id >= 0) {
            int[] supportedTypes = WatchFace.getSupportedComplications(id);

            startActivityForResult(ComplicationHelperActivity.createProviderChooserHelperIntent(
                    getApplicationContext(), watchFaceComponent, id, supportedTypes
            ), COMPLICATION_CONFIG_REQUEST_CODE);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.d(TAG, "Ambient complications were changed");
        if (buttonView == complicationsInAmbientSwitch) {
            complicationsInAmbient = isChecked;
        } else if (buttonView == emptyComplicationsSwitch) {
            emptyComplications = isChecked;
        }
    }
}
