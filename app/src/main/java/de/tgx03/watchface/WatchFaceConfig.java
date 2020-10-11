package de.tgx03.watchface;

import android.app.Activity;
import android.content.ComponentName;
import android.os.Bundle;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.view.View;

import java.util.concurrent.Executors;

public class WatchFaceConfig extends Activity {

    private static final short COMPLICATION_CONFIG_REQUEST_CODE = 1001;

    private ComponentName watchFaceComponent;

    private ProviderInfoRetriever infoRetriever;

    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        watchFaceComponent = new ComponentName(getApplicationContext(), WatchFace.class);

        setContentView(R.layout.settings_list);

        infoRetriever = new ProviderInfoRetriever(getApplicationContext(), Executors.newCachedThreadPool());
        infoRetriever.init();
    }

    protected void onDestroy() {
        super.onDestroy();
        infoRetriever.release();
    }

    public void chooseBackground(View view) {
        launchComplicationHelperActivity(ComplicationID.BACKGROUND);
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
