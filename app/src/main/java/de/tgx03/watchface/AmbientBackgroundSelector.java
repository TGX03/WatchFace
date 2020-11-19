package de.tgx03.watchface;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Switch;

import static de.tgx03.watchface.WatchFace.complicationInAmbient;

public class AmbientBackgroundSelector extends Activity {

    private static final String TAG = "AmbientSelector";

    private final Switch[] switches = new Switch[6];

    protected void onCreate(Bundle savedInstance) {
        Log.d(TAG, "Creating activity");
        super.onCreate(savedInstance);

        setContentView(R.layout.ambient_background);
        Bitmap watchface = getIntent().getParcelableExtra("background");
        ImageView preview = findViewById(R.id.ambientpreview);
        if (watchface != null) {
            preview.setImageBitmap(watchface);
        }
        initializeSwitches();
    }

    private void initializeSwitches() {
        Log.d(TAG, "Getting switches");
        switches[0] = findViewById(R.id.background_ambient);
        switches[1] = findViewById(R.id.top_ambient);
        switches[2] = findViewById(R.id.bot_large_ambient);
        switches[3] = findViewById(R.id.bot_left);
        switches[4] = findViewById(R.id.bot_middle);
        switches[5] = findViewById(R.id.bot_right);
        for (int i = 0; i < switches.length; i++) {
            switches[i].setChecked(complicationInAmbient[i]);
        }
    }

    public void background(View view) {
        Log.d(TAG, (switches[0].isChecked() ? "Enabling" : "Disabling") + " background complication in ambient");
        complicationInAmbient[0] = switches[0].isChecked();
    }

    public void buttonTop(View view) {
        Log.d(TAG, (switches[1].isChecked() ? "Enabling" : "Disabling") + " top complication in ambient");
        complicationInAmbient[1] = switches[1].isChecked();
    }

    public void buttonBottom(View view) {
        Log.d(TAG, (switches[2].isChecked() ? "Enabling" : "Disabling") + " large bottom complication in ambient");
        complicationInAmbient[2] = switches[2].isChecked();
    }

    public void buttonLeft(View view) {
        Log.d(TAG, (switches[3].isChecked() ? "Enabling" : "Disabling") + " left complication in ambient");
        complicationInAmbient[3] = switches[3].isChecked();
    }

    public void buttonMiddle(View view) {
        Log.d(TAG, (switches[4].isChecked() ? "Enabling" : "Disabling") + " middle complication in ambient");
        complicationInAmbient[4] = switches[4].isChecked();
    }

    public void buttonRight(View view) {
        Log.d(TAG, (switches[5].isChecked() ? "Enabling" : "Disabling") + " right complication in ambient");
        complicationInAmbient[5] = switches[5].isChecked();
    }
}
