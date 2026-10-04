package io.github.alexrodri4.cuadernohierro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

public class AlarmReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i) {
    String a = i.getAction() == null ? "" : i.getAction();
    if (Core.ACT_REST.equals(a)) {
      Core.prefs(c).edit().remove("restEnd").apply();
      CdhWidget.updateAll(c);
      if (Core.visible) return; // con la app abierta avisa la propia app
      JSONObject n = Core.data(c).optJSONObject("notif");
      if (n != null && !n.optBoolean("rest", true)) return;
      Core.notify(c, Core.N_REST, Core.CH_REST, "¡Descanso terminado!",
        Core.prefs(c).getString("restLabel", "A por la siguiente serie"), 0xFF35F29A, Core.openApp(c, null, 20), null, null);
    } else if (Steps.ACT_STEPS.equals(a)) {
      final PendingResult r = goAsync();
      Steps.sample(c, () -> { try { r.finish(); } catch (Exception e) { } });
    } else if (Core.ACT_DAILY.equals(a)) {
      Core.daily(c); Core.scheduleDaily(c); CdhWidget.updateAll(c);
    } else {
      Core.scheduleDaily(c); CdhWidget.updateAll(c); Steps.schedule(c);
    }
  }
}
