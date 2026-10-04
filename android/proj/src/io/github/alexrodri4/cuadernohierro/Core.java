package io.github.alexrodri4.cuadernohierro;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Datos compartidos, notificaciones y alarmas. */
final class Core {
  static final String PREFS = "cdh";
  static final String CH_REST = "descanso", CH_REMIND = "recordatorios";
  static final String ACT_REST = "io.github.alexrodri4.cuadernohierro.REST";
  static final String ACT_DAILY = "io.github.alexrodri4.cuadernohierro.DAILY";
  static final String ACT_MIDNIGHT = "io.github.alexrodri4.cuadernohierro.MIDNIGHT";
  static final int N_REST = 1, N_TRAIN = 2, N_STREAK = 3, N_CYCLE = 4, N_TEST = 9;
  static volatile boolean visible = false;

  static final String[] PH_KEY = {"men", "fol", "ovu", "lut", "lut2"};
  static final String[] PH_NAME = {"Menstrual", "Folicular", "Ovulatoria", "Lútea", "Lútea tardía"};
  static final int[] PH_COLOR = {0xFFF0667D, 0xFF4CC79C, 0xFFF3B343, 0xFFA58BE0, 0xFFC3B0EE};
  static final String[] PH_TIP = {
    "Entrena según cómo te encuentres. Si hay dolor o cansancio, baja el peso un 10–20 % y prioriza la técnica.",
    "Muchas mujeres notan más energía en esta fase. Buen momento para subir cargas e intentar récords.",
    "Suele coincidir con el pico de energía. Aprovecha para series pesadas y calienta bien las articulaciones.",
    "El esfuerzo puede sentirse mayor. Mantén las cargas, hidrátate y alarga un poco los descansos.",
    "Si notas síntomas premenstruales, reduce volumen y céntrate en técnica y constancia."};

  static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

  static JSONObject data(Context c) {
    try { return new JSONObject(prefs(c).getString("data", "{}")); } catch (Exception e) { return new JSONObject(); }
  }

  // ---------- fechas ----------
  static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
  static String ymd(Calendar k) { return F.format(k.getTime()); }
  static Calendar cal(String s) {
    Calendar k = Calendar.getInstance();
    try { Date d = F.parse(s); k.setTime(d); } catch (Exception e) { }
    k.set(Calendar.HOUR_OF_DAY, 12); k.set(Calendar.MINUTE, 0); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0);
    return k;
  }
  static Calendar today() { return cal(ymd(Calendar.getInstance())); }
  static int daysBetween(Calendar a, Calendar b) { return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0); }
  /** 0 = lunes ... 6 = domingo */
  static int dow(Calendar k) { return (k.get(Calendar.DAY_OF_WEEK) + 5) % 7; }

  static Set<String> trained(JSONObject d) {
    Set<String> s = new HashSet<>();
    JSONArray a = d.optJSONArray("trained");
    if (a != null) for (int i = 0; i < a.length(); i++) s.add(a.optString(i));
    return s;
  }
  static boolean[] week(JSONObject d) {
    Set<String> t = trained(d); boolean[] w = new boolean[7];
    Calendar mon = today(); mon.add(Calendar.DAY_OF_MONTH, -dow(mon));
    for (int i = 0; i < 7; i++) { Calendar k = (Calendar) mon.clone(); k.add(Calendar.DAY_OF_MONTH, i); w[i] = t.contains(ymd(k)); }
    return w;
  }
  static int count(boolean[] w) { int n = 0; for (boolean b : w) if (b) n++; return n; }

  /** Devuelve {día, fase} o null. */
  static int[] cycleOn(JSONObject d, Calendar date) {
    JSONObject c = d.optJSONObject("cycle");
    if (!d.optBoolean("f") || c == null) return null;
    Calendar last = cal(c.optString("last"));
    int L = Math.max(18, c.optInt("L", 28)), P = c.optInt("P", 5);
    int diff = daysBetween(last, date); if (diff < 0) return null;
    int day = (diff % L) + 1, O = L - 14, ph;
    if (day <= P) ph = 0; else if (day >= O - 1 && day <= O + 1) ph = 2; else if (day < O - 1) ph = 1; else if (day >= L - 4) ph = 4; else ph = 3;
    return new int[]{day, ph, L};
  }

  // ---------- notificaciones ----------
  static void channels(Context c) {
    if (Build.VERSION.SDK_INT < 26) return;
    NotificationManager nm = c.getSystemService(NotificationManager.class);
    NotificationChannel r = new NotificationChannel(CH_REST, "Fin del descanso", NotificationManager.IMPORTANCE_HIGH);
    r.setDescription("Aviso cuando termina el descanso entre series");
    r.enableVibration(true); r.setVibrationPattern(new long[]{0, 300, 150, 300, 150, 500});
    nm.createNotificationChannel(r);
    NotificationChannel m = new NotificationChannel(CH_REMIND, "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT);
    m.setDescription("Recordatorios de entreno, racha y ciclo");
    nm.createNotificationChannel(m);
  }
  static boolean allowed(Context c) {
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    return Build.VERSION.SDK_INT < 24 || nm.areNotificationsEnabled();
  }
  static PendingIntent openApp(Context c, String action, int code) {
    Intent i = new Intent(c, MainActivity.class);
    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    if (action != null) i.putExtra("cdh_action", action);
    return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }
  @SuppressWarnings("deprecation")
  static void notify(Context c, int id, String ch, String title, String text, int color, PendingIntent tap, String actLabel, PendingIntent act) {
    channels(c);
    if (!allowed(c)) return;
    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ch) : new Notification.Builder(c);
    try { b.setLargeIcon(android.graphics.BitmapFactory.decodeResource(c.getResources(), R.drawable.ic_notif_large)); } catch (Exception e) { }
    b.setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
     .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true).setColor(color).setContentIntent(tap);
    if (Build.VERSION.SDK_INT < 26) {
      b.setPriority(CH_REST.equals(ch) ? Notification.PRIORITY_HIGH : Notification.PRIORITY_DEFAULT);
      b.setDefaults(Notification.DEFAULT_ALL);
    }
    if (CH_REST.equals(ch)) b.setCategory(Notification.CATEGORY_ALARM);
    if (act != null) b.addAction(new Notification.Action.Builder(null, actLabel, act).build());
    try { ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, b.build()); } catch (Exception e) { }
  }
  static void cancel(Context c, int id) { ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id); }

  // ---------- alarmas ----------
  static PendingIntent alarmPI(Context c, String action, int code) {
    Intent i = new Intent(c, AlarmReceiver.class).setAction(action);
    return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }
  static void setExact(Context c, long at, PendingIntent pi) {
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    try {
      if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
      else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    } catch (Exception e) { am.set(AlarmManager.RTC_WAKEUP, at, pi); }
  }
  static void scheduleRest(Context c, long inMs, String label) {
    long end = System.currentTimeMillis() + Math.max(1000, inMs);
    prefs(c).edit().putString("restLabel", label).putLong("restEnd", end).apply();
    cancel(c, N_REST);
    setExact(c, end, alarmPI(c, ACT_REST, 10));
    CdhWidget.updateAll(c);
  }
  static void cancelRest(Context c) {
    ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(alarmPI(c, ACT_REST, 10));
    prefs(c).edit().remove("restEnd").apply();
    CdhWidget.updateAll(c);
  }
  static void scheduleDaily(Context c) {
    JSONObject n = data(c).optJSONObject("notif");
    int h = n != null ? n.optInt("hour", 18) : 18, m = n != null ? n.optInt("min", 0) : 0;
    Calendar k = Calendar.getInstance();
    k.set(Calendar.HOUR_OF_DAY, h); k.set(Calendar.MINUTE, m); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0);
    if (k.getTimeInMillis() <= System.currentTimeMillis() + 5000) k.add(Calendar.DAY_OF_MONTH, 1);
    setExact(c, k.getTimeInMillis(), alarmPI(c, ACT_DAILY, 11));
    Calendar mid = Calendar.getInstance();
    mid.add(Calendar.DAY_OF_MONTH, 1); mid.set(Calendar.HOUR_OF_DAY, 0); mid.set(Calendar.MINUTE, 1); mid.set(Calendar.SECOND, 0);
    ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC, mid.getTimeInMillis(), alarmPI(c, ACT_MIDNIGHT, 12));
  }

  /** Revisión diaria a la hora elegida. */
  static void daily(Context c) {
    JSONObject d = data(c); JSONObject n = d.optJSONObject("notif");
    if (n == null) return;
    Calendar t = today(); String ts = ymd(t);
    if (ts.equals(prefs(c).getString("dailyDone", ""))) return;
    prefs(c).edit().putString("dailyDone", ts).apply();
    boolean[] w = week(d); int cnt = count(w), goal = Math.max(1, d.optInt("goal", 4));
    boolean today = w[dow(t)];
    int left = 6 - dow(t) + 1; // días restantes incluido hoy
    int need = goal - cnt, streak = d.optInt("streak", 0);
    String next = d.optString("next", "");
    boolean sentStreak = false;
    if (n.optBoolean("streak") && streak > 0 && need > 0 && dow(t) >= 5) {
      String txt = need > left
        ? "Esta semana ya no llegas al objetivo" + (d.optInt("wild", 0) > 0 ? ": se usará un comodín para salvar la racha." : ". Entrena hoy igualmente para no perder ritmo.")
        : "Te falta" + (need == 1 ? " 1 día" : "n " + need + " días") + " para mantener tu racha de " + streak + (streak == 1 ? " semana." : " semanas.");
      notify(c, N_STREAK, CH_REMIND, "Racha en peligro", txt, 0xFFF5C542, openApp(c, "start", 21), "Empezar", openApp(c, "start", 22));
      sentStreak = true;
    }
    if (!sentStreak && n.optBoolean("train") && !today && need > 0) {
      String txt = (next.isEmpty() ? "Hoy toca entrenar." : "Hoy toca " + next + ".") + " Llevas " + cnt + "/" + goal + " días esta semana.";
      notify(c, N_TRAIN, CH_REMIND, "¿Entrenamos?", txt, 0xFF35F29A, openApp(c, null, 23), "Empezar", openApp(c, "start", 24));
    }
    if (n.optBoolean("cycle")) {
      int[] cd = cycleOn(d, t);
      if (cd != null) {
        int until = cd[2] - cd[0] + 1;
        Calendar y = (Calendar) t.clone(); y.add(Calendar.DAY_OF_MONTH, -1);
        int[] py = cycleOn(d, y);
        if (until == 2) notify(c, N_CYCLE, CH_REMIND, "Tu regla llegaría en 2 días", "Estimación según tus ciclos registrados. " + PH_TIP[4], PH_COLOR[0], openApp(c, null, 25), null, null);
        else if (py != null && py[1] != cd[1]) notify(c, N_CYCLE, CH_REMIND, "Empieza tu fase " + PH_NAME[cd[1]].toLowerCase(), PH_TIP[cd[1]], PH_COLOR[cd[1]], openApp(c, null, 26), null, null);
      }
    }
  }
}
