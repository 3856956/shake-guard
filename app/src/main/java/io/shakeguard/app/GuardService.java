package io.shakeguard.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

public final class GuardService extends AccessibilityService {
    static volatile boolean connected;
    private final RuleEngine engine = new RuleEngine();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private String pendingSource, pendingTarget;
    private boolean pending;
    private final Runnable timeout = () -> finishPending("返回后未确认回到来源");
    private final SharedPreferences.OnSharedPreferenceChangeListener changes = (p, key) -> {
        if ("enabled".equals(key) || "rules".equals(key) || "pauseUntil".equals(key)) {
            engine.reset(); cancelPending();
        }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { engine.reset(); cancelPending(); }
    };
    @Override protected void onServiceConnected() {
        connected = true;
        store = new Store(this);
        store.prefs.registerOnSharedPreferenceChangeListener(changes);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, filter);
        engine.reset();
        store.log("服务", getPackageName(), "无障碍服务已连接 v" + BuildConfig.VERSION_NAME);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (store == null) return;
        long now = SystemClock.elapsedRealtime();
        if (!store.enabled() || store.pauseRemaining() > 0 || getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) trace(event, "未检测：总开关关闭、暂停或锁屏");
            engine.reset(); cancelPending(); return;
        }
        if (event.getPackageName() == null) return;
        // getEventTime uses uptimeMillis, not elapsedRealtime (which includes sleep).
        long uptime = SystemClock.uptimeMillis();
        if (!EventTiming.fresh(event.getEventTime(), uptime)) {
            trace(event, "忽略过期或异常事件，uptime差=" + (uptime-event.getEventTime()) + "ms"); return;
        }
        String pkg = event.getPackageName().toString();
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            engine.click(pkg, now); trace(event, "收到点击事件（不读取文字）"); return;
        }
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        // Content/pane events are not reliable evidence of an application switch.
        if (event.getContentChangeTypes() != 0) { engine.pane(pkg); trace(event, engine.reason()); return; }
        if (pending) {
            if (pkg.equals(pendingSource)) finishPending("已确认返回来源");
            else if (!pkg.equals(pendingTarget)) finishPending("返回后进入其他窗口，未确认");
        }
        RuleEngine.Match match = engine.window(pkg, now, store.rules());
        trace(event, store.rules().isEmpty() ? "没有配置规则，" + engine.reason() : engine.reason());
        if (match == null) return;
        RuleEngine.Rule rule = match.rule;
        if (!rule.automatic) { store.log(rule.source, rule.target, "仅记录：保护时段内未观察到近期点击"); return; }
        boolean success = performGlobalAction(GLOBAL_ACTION_BACK);
        store.log(rule.source, rule.target, success ? "已发出返回指令，等待确认" : "系统未接受返回指令");
        if (success) {
            pending = true; pendingSource = rule.source; pendingTarget = rule.target;
            handler.postDelayed(timeout, 2000);
        }
    }
    private void trace(AccessibilityEvent event, String reason) {
        if (store.diagnosing()) store.log("诊断 " + event.getEventType(), String.valueOf(event.getPackageName()), reason);
    }
    private void finishPending(String result) {
        if (!pending) return;
        String source = pendingSource, target = pendingTarget;
        cancelPending();
        store.log(source, target, result);
    }
    private void cancelPending() { pending = false; handler.removeCallbacks(timeout); pendingSource = null; pendingTarget = null; }
    @Override public void onInterrupt() { engine.reset(); cancelPending(); }
    @Override public void onDestroy() {
        connected = false;
        cancelPending(); engine.reset();
        if (store != null) { store.prefs.unregisterOnSharedPreferenceChangeListener(changes); unregisterReceiver(screen); }
        super.onDestroy();
    }
}
