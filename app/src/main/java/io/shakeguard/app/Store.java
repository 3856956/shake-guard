package io.shakeguard.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class Store {
    final SharedPreferences prefs;
    private final String boot;
    Store(Context context) {
        prefs = context.getSharedPreferences("guard", Context.MODE_PRIVATE);
        boot = String.valueOf(Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1));
    }
    boolean enabled() { return prefs.getBoolean("enabled", false); }
    void enabled(boolean enabled) { prefs.edit().putBoolean("enabled", enabled).apply(); }
    long pauseRemaining() {
        if (!boot.equals(prefs.getString("pauseBoot", ""))) return 0;
        return Math.max(0, prefs.getLong("pauseUntil", 0) - SystemClock.elapsedRealtime());
    }
    void pause(boolean pause) { prefs.edit().putString("pauseBoot", boot).putLong("pauseUntil", pause ? SystemClock.elapsedRealtime()+300000 : 0).apply(); }
    void startDiagnostics() { prefs.edit().putString("diagnosticBoot", boot).putLong("diagnosticUntil", SystemClock.elapsedRealtime()+60000).apply(); }
    boolean diagnosing() { return boot.equals(prefs.getString("diagnosticBoot", "")) && SystemClock.elapsedRealtime()<prefs.getLong("diagnosticUntil", 0); }
    void stopDiagnostics() { prefs.edit().remove("diagnosticUntil").apply(); }
    List<RuleEngine.Rule> rules() {
        List<RuleEngine.Rule> rules = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("rules", "[]"));
            for (int i=0; i<array.length(); i++) {
                JSONObject r = array.getJSONObject(i);
                rules.add(new RuleEngine.Rule(r.getString("source"), r.getString("target"), r.getInt("seconds"), r.getBoolean("automatic")));
            }
        } catch (JSONException ignored) { /* Fail closed: no rules on corrupt data. */ }
        return rules;
    }
    void rules(List<RuleEngine.Rule> rules) {
        JSONArray array = new JSONArray();
        for (RuleEngine.Rule r : rules) {
            JSONObject obj = new JSONObject();
            try { obj.put("source", r.source).put("target", r.target).put("seconds", r.seconds).put("automatic", r.automatic); }
            catch (JSONException e) { throw new IllegalStateException(e); }
            array.put(obj);
        }
        prefs.edit().putString("rules", array.toString()).apply();
    }
    JSONArray logs() {
        JSONArray result = new JSONArray();
        try {
            JSONArray old = new JSONArray(prefs.getString("logs", "[]"));
            long cutoff = System.currentTimeMillis() - 7L*24*60*60*1000;
            for (int i=0; i<old.length(); i++) if (old.getJSONObject(i).getLong("time") >= cutoff) result.put(old.getJSONObject(i));
        } catch (JSONException ignored) { }
        // Also prune persisted records when opening the log screen.
        prefs.edit().putString("logs", result.toString()).apply();
        return result;
    }
    void log(String source, String target, String result) {
        JSONArray old = logs(), next = new JSONArray();
        try { next.put(new JSONObject().put("time", System.currentTimeMillis()).put("source", source).put("target", target).put("result", result)); }
        catch (JSONException e) { throw new IllegalStateException(e); }
        for (int i=0; i<Math.min(old.length(), 199); i++) next.put(old.opt(i));
        prefs.edit().putString("logs", next.toString()).apply();
    }
    void clearLogs() { prefs.edit().remove("logs").apply(); }
}
