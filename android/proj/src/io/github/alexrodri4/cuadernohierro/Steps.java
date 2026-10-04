package io.github.alexrodri4.cuadernohierro;

import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Calendar;
import java.util.Iterator;
import org.json.JSONObject;

/** Cuenta pasos con el sensor del móvil (TYPE_STEP_COUNTER) y los reparte por días. */
public class Steps {
  static final String ACT_STEPS = "io.github.alexrodri4.cuadernohierro.STEPS";

  static Sensor sensor(Context c) {
    SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
    return sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
  }
  static boolean available(Context c) { return sensor(c) != null; }
  static boolean permitted(Context c) {
    return Build.VERSION.SDK_INT < 29 || c.checkSelfPermission("android.permission.ACTIVITY_RECOGNITION") == PackageManager.PERMISSION_GRANTED;
  }
  static void schedule(Context c) {
    if (!available(c) || !permitted(c)) return;
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 60000,
      AlarmManager.INTERVAL_HALF_HOUR, Core.alarmPI(c, ACT_STEPS, 13));
  }
  static void sample(final Context c, final Runnable done) {
    final Sensor s = sensor(c);
    if (s == null || !permitted(c)) { if (done != null) done.run(); return; }
    final SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
    final Handler h = new Handler(Looper.getMainLooper());
    final boolean[] fin = {false};
    final SensorEventListener[] L = new SensorEventListener[1];
    L[0] = new SensorEventListener() {
      @Override public void onSensorChanged(SensorEvent e) {
        if (fin[0]) return; fin[0] = true;
        sm.unregisterListener(this);
        record(c, (long) e.values[0]);
        if (done != null) done.run();
      }
      @Override public void onAccuracyChanged(Sensor x, int a) { }
    };
    try { sm.registerListener(L[0], s, SensorManager.SENSOR_DELAY_NORMAL, h); }
    catch (Exception e) { if (done != null) done.run(); return; }
    h.postDelayed(() -> { if (!fin[0]) { fin[0] = true; sm.unregisterListener(L[0]); if (done != null) done.run(); } }, 8000);
  }
  static synchronized void record(Context c, long total) {
    SharedPreferences p = Core.prefs(c);
    long last = p.getLong("stepTotal", -1);
    long delta = last < 0 ? 0 : (total >= last ? total - last : total);
    if (delta > 60000) delta = 0;
    String day = Core.ymd(Calendar.getInstance());
    try {
      JSONObject j = new JSONObject(p.getString("stepDays", "{}"));
      j.put(day, j.optLong(day, 0) + delta);
      if (j.length() > 400) { Iterator<String> it = j.keys(); String old = it.next(); j.remove(old); }
      p.edit().putString("stepDays", j.toString()).putLong("stepTotal", total).putLong("stepAt", System.currentTimeMillis()).apply();
    } catch (Exception e) { }
  }
  static String json(Context c) { return Core.prefs(c).getString("stepDays", "{}"); }
}
