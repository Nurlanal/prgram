package az.bakurunner.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;

/**
 * Native helpers exposed to the game as window.AndroidBridge.
 * Progress (coins, best score, characters, settings) is stored locally in
 * SharedPreferences - nothing ever leaves the device.
 */
public class GameBridge {
    private static final String PREFS = "baku_runner";
    private static final String KEY = "save_v1";
    private final Context context;
    private final SharedPreferences prefs;

    GameBridge(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @JavascriptInterface
    public String load() {
        return prefs.getString(KEY, "");
    }

    @JavascriptInterface
    public void save(String json) {
        if (json == null || json.length() > 100_000) return;
        prefs.edit().putString(KEY, json).apply();
    }

    @JavascriptInterface
    @SuppressWarnings("deprecation")
    public void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            int d = Math.max(10, Math.min(ms, 500));
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(d);
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public boolean isAndroid() {
        return true;
    }
}
