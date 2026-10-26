package io.shakeguard.app;

import java.util.List;

/** No Android dependency. Times are monotonic milliseconds. */
public final class RuleEngine {
    public static final class Rule {
        public final String source, target;
        public final int seconds;
        public final boolean automatic;
        public Rule(String source, String target, int seconds, boolean automatic) {
            this.source = source; this.target = target; this.seconds = seconds; this.automatic = automatic;
        }
    }
    public static final class Match {
        public final Rule rule;
        Match(Rule rule) { this.rule = rule; }
    }
    private String foreground;
    private long entered, clicked = -1, cooldownUntil;
    private String reason = "尚未观察到来源";
    public String reason() { return reason; }
    public void pane(String pkg) {
        // Same-app pane titles must not destroy a valid startup interval.
        if (!pkg.equals(foreground)) { reset(); reason = "其他包的分区事件，无法确认前台，已清除来源"; }
        else reason = "同包界面分区变化，保留原保护时段";
    }
    public void reset() { foreground = null; entered = 0; clicked = -1; cooldownUntil = 0; }
    public void click(String pkg, long now) { if (pkg.equals(foreground)) clicked = now; }
    public Match window(String pkg, long now, List<Rule> rules) {
        if (pkg.equals(foreground)) { reason = "同包重复窗口，不延长保护时段"; return null; }
        String previous = foreground;
        long previousEntered = entered, previousClicked = clicked;
        foreground = pkg; entered = now; clicked = -1;
        if (previous == null) { reason = "首次窗口，仅建立来源状态"; return null; }
        if (now < cooldownUntil) { reason = "匹配冷却中"; return null; }
        reason = "未配置直接跳转规则：" + previous + " → " + pkg;
        for (Rule rule : rules) {
            if (previous.equals(rule.source) && pkg.equals(rule.target)) {
                if (now < previousEntered || now - previousEntered > rule.seconds * 1000L) {
                    reason = "保护时段已过：" + (now - previousEntered) + "ms / " + rule.seconds + "秒"; return null;
                }
                if (previousClicked >= 0 && now - previousClicked <= 1500) {
                    reason = "近期收到来源点击，放行：" + (now - previousClicked) + "ms"; return null;
                }
                cooldownUntil = now + 3000;
                reason = rule.automatic ? "匹配自动返回规则" : "匹配仅记录规则";
                return new Match(rule);
            }
        }
        return null;
    }
}
