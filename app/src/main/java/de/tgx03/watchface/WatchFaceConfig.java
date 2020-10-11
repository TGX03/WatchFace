package de.tgx03.watchface;

import android.app.Activity;
import android.content.ComponentName;
import android.os.Bundle;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.view.View;

public class WatchFaceConfig extends Activity {

    private static final short COMPLICATION_CONFIG_REQUEST_CODE = 1001;

    private ComponentName watchFaceComponent;

    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        watchFaceComponent = new ComponentName(getApplicationContext(), WatchFace.class);

        setContentView(R.layout.settings_list);
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    public void chooseBackground(View view) {
        launchComplicationHelperActivity(ComplicationID.BACKGROUND);
    }

    public void chooseTopComplication(View view) {
        launchComplicationHelperActivity(ComplicationID.TOP);
    }

    public void chooseBottomComplication(View view) {
        if (view.equals(findViewById(R.id.SelectMiddleComplication))) {
            launchComplicationHelperActivity(ComplicationID.BOTTOM_MIDDLE);
        } else if (view.equals(findViewById(R.id.SelectLeftComplication))) {
            launchComplicationHelperActivity(ComplicationID.BOTTOM_LEFT);
        } else if (view.equals(findViewById(R.id.SelectRightComplication))) {
            launchComplicationHelperActivity(ComplicationID.BOTTOM_RIGHT);
        }
    }

    private void launchComplicationHelperActivity(ComplicationID id) {
        byte selectedComplication = WatchFace.getComplicationID(id);

        if (selectedComplication >= 0) {
            int[] supportedTypes = WatchFace.getSupportedComplications(id);

            startActivityForResult(ComplicationHelperActivity.createProviderChooserHelperIntent(
                    getApplicationContext(), watchFaceComponent, selectedComplication, supportedTypes
            ), COMPLICATION_CONFIG_REQUEST_CODE);
        }
    }
}
