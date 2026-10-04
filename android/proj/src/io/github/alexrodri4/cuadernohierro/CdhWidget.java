package io.github.alexrodri4.cuadernohierro;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import java.util.Calendar;
import org.json.JSONObject;

public class CdhWidget extends AppWidgetProvider {
  static final int[] DOTS = {R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6};

  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) m.updateAppWidget(id, build(c)); }

  static void updateAll(Context c) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    int[] ids = m.getAppWidgetIds(new ComponentName(c, CdhWidget.class));
    if (ids.length > 0) for (int id : ids) m.updateAppWidget(id, build(c));
  }

  static RemoteViews build(Context c) {
    JSONObject d = Core.data(c);
    RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
    int streak = d.optInt("streak", 0), goal = Math.max(1, d.optInt("goal", 4));
    v.setTextViewText(R.id.w_streak, "Racha " + streak + (streak == 1 ? " semana" : " sem"));
    boolean[] w = Core.week(d); Calendar t = Core.today(); int td = Core.dow(t);
    for (int i = 0; i < 7; i++) {
      boolean today = i == td;
      int bg = w[i] ? (today ? R.drawable.dot_today_on : R.drawable.dot_on) : (today ? R.drawable.dot_today : R.drawable.dot_off);
      v.setInt(DOTS[i], "setBackgroundResource", bg);
      v.setTextColor(DOTS[i], w[i] ? 0xFF06100B : (today ? 0xFFF2F5F6 : 0xFFA1AAAF));
    }
    int cnt = Core.count(w);
    v.setTextViewText(R.id.w_week, cnt + "/" + goal + " días esta semana");
    String next = d.optString("next", "");
    boolean doneToday = w[td];
    v.setTextViewText(R.id.w_next, doneToday ? "Hoy ya has entrenado 💪" : (next.isEmpty() ? "Crea una rutina en la app" : "Próximo: " + next));
    v.setTextViewText(R.id.w_btn, doneToday ? "Ver" : "Empezar");
    int[] cd = Core.cycleOn(d, t);
    if (cd != null) {
      v.setViewVisibility(R.id.w_cycle, View.VISIBLE);
      v.setTextViewText(R.id.w_cycle, "Día " + cd[0] + " · " + Core.PH_NAME[cd[1]]);
      v.setTextColor(R.id.w_cycle, Core.PH_COLOR[cd[1]]);
    } else v.setViewVisibility(R.id.w_cycle, View.GONE);
    long end = Core.prefs(c).getLong("restEnd", 0), left = end - System.currentTimeMillis();
    if (left > 0) {
      v.setViewVisibility(R.id.w_rest, View.VISIBLE);
      v.setViewVisibility(R.id.w_main, View.GONE);
      v.setTextViewText(R.id.w_rest_next, Core.prefs(c).getString("restLabel", "A por la siguiente serie"));
      v.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime() + left, null, true);
      v.setChronometerCountDown(R.id.w_chrono, true);
    } else {
      v.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime(), null, false);
      v.setViewVisibility(R.id.w_rest, View.GONE);
      v.setViewVisibility(R.id.w_main, View.VISIBLE);
    }
    v.setOnClickPendingIntent(R.id.w_root, Core.openApp(c, null, 30));
    v.setOnClickPendingIntent(R.id.w_mic, Core.openApp(c, "voice", 32));
    v.setViewVisibility(R.id.w_mic, Core.prefs(c).getString("apiKey", "").isEmpty() ? View.GONE : View.VISIBLE);
    v.setOnClickPendingIntent(R.id.w_btn, Core.openApp(c, doneToday ? null : "start", 31));
    return v;
  }
}
