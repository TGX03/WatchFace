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

public class WatchFaceConfig extends Activity implements CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "WatchFaceConfig";

    private static final short COMPLICATION_CONFIG_REQUEST_CODE = 1001;

    private ComponentName watchFaceComponent;

    private Switch complicationsInAmbient;

    protected void onCreate(Bundle savedInstance) {
        Log.d(TAG, "Creating config activity");

        super.onCreate(savedInstance);

        watchFaceComponent = new ComponentName(getApplicationContext(), WatchFace.class);

        setContentView(R.layout.settings_list);
        complicationsInAmbient = findViewById(R.id.ComplicationsInAmbient);
        complicationsInAmbient.setChecked(WatchFace.getComplicationsInAmbient());
        complicationsInAmbient.setOnCheckedChangeListener(this);

        try {
            Log.d(TAG, "Getting watchface screenshot");
            Bitmap watchFace = WatchFace.getEngine().screenshot();
            ImageView image = findViewById(R.id.settingspreview);
            image.setImageBitmap(watchFace);
            boolean[] bounds = WatchFace.getEngine().complicationLocations();
            Drawable complicationSet = getDrawable(R.drawable.added_complication);
            if (bounds[0]) {
                ImageButton topButton = findViewById(R.id.SelectTopComplication);
                topButton.setImageDrawable(complicationSet);
            }
            if (bounds[1]) {
                ImageButton bottomLargeButton = findViewById(R.id.SelectBottomLargeComplication);
                bottomLargeButton.setImageDrawable(complicationSet);
            }
            if (bounds[2]) {
                ImageButton leftButton = findViewById(R.id.SelectLeftComplication);
                leftButton.setImageDrawable(complicationSet);
            }
            if (bounds[3]) {
                ImageButton middleButton = findViewById(R.id.SelectMiddleComplication);
                middleButton.setImageDrawable(complicationSet);
            }
            if (bounds[4]) {
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

    public void chooseBackground(View view) {
        Log.d(TAG, "Choosing background complication");
        launchComplicationHelperActivity(WatchFace.BACKGROUND_COMPLICATION);
    }

    public void chooseTopComplication(View view) {
        Log.d(TAG, "Choosing top complication");
        launchComplicationHelperActivity(WatchFace.TOP_COMPLICATION);
    }

    public void chooseBottomLargeComplication(View view) {
        Log.d(TAG, "Choosing large bottom complication");
        launchComplicationHelperActivity(WatchFace.BOTTOM_LARGE_COMPLICATION);
    }

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
        if (buttonView == complicationsInAmbient) {
            WatchFace.setComplicationsInAmbient(isChecked);
        }
    }
}
