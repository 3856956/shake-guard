package io.shakeguard.app;

/** Accessibility event timestamps use uptime, which excludes deep sleep. */
final class EventTiming {
    static boolean fresh(long eventUptime, long currentUptime) {
        return eventUptime > 0 && eventUptime <= currentUptime && currentUptime - eventUptime <= 1000;
    }
}
