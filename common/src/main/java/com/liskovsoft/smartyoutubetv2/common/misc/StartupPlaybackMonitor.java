package com.liskovsoft.smartyoutubetv2.common.misc;

import java.util.Objects;

/** Monotonic-clock samples; no networking or player operations run here. */
public final class StartupPlaybackMonitor {
    public enum Result { NONE, RECOVER, HEALTHY }
    private String videoId;
    private long lastPosition = -1;
    private long highWater;
    private long lastProgressAt;
    private long firstRollbackAt = -1;
    private long stableSince = -1;
    private long cooldownUntil;
    private int rollbacks;
    private int attempts;
    private boolean confirmed;
    private long firstSourceErrorAt = -1;
    private int sourceErrors;

    public void begin(String id) {
        if (!Objects.equals(videoId, id)) {
            videoId = id;
            attempts = 0;
            cooldownUntil = 0;
            firstSourceErrorAt = -1;
            sourceErrors = 0;
            resetObservation();
        }
    }

    public void resetObservation() {
        lastPosition = -1;
        highWater = 0;
        firstRollbackAt = stableSince = -1;
        rollbacks = 0;
        confirmed = false;
    }

    public void onReload() {
        stableSince = -1;
        confirmed = false;
    }

    /** Unlike position samples, source failures survive seek restoration after reload. */
    public Result onSourceError(long now, long position) {
        if (position < 0 || position > 10_000 || now < cooldownUntil || attempts >= 3) return Result.NONE;
        if (firstSourceErrorAt < 0 || now - firstSourceErrorAt > 10_000) {
            firstSourceErrorAt = now;
            sourceErrors = 0;
        }
        return ++sourceErrors >= 2 ? recover(now) : Result.NONE;
    }

    private Result recover(long now) {
        attempts++;
        cooldownUntil = now + 15_000;
        firstSourceErrorAt = -1;
        sourceErrors = 0;
        resetObservation();
        return Result.RECOVER;
    }

    public Result sample(long now, long position, boolean playing) {
        if (position < 0 || now < cooldownUntil) return Result.NONE;
        if (lastPosition < 0) {
            lastPosition = highWater = position;
            lastProgressAt = now;
            return Result.NONE;
        }
        long delta = position - lastPosition;
        if (playing && delta > 0 && delta <= 2_000) {
            if (stableSince < 0) stableSince = now;
        } else {
            stableSince = -1;
        }
        if (position > highWater) {
            highWater = position;
            lastProgressAt = now;
        }
        if (position <= 10_000 && lastPosition <= 10_000 && delta <= -750) {
            if (firstRollbackAt < 0 || now - firstRollbackAt > 10_000) {
                firstRollbackAt = now;
                rollbacks = 0;
            }
            rollbacks++;
        }
        lastPosition = position;
        if (attempts < 3 && position <= 10_000 &&
                ((rollbacks >= 2 && now - firstRollbackAt <= 10_000) || now - lastProgressAt >= 10_000)) {
            return recover(now);
        }
        if (!confirmed && stableSince >= 0 && now - stableSince >= 10_000) {
            confirmed = true;
            return Result.HEALTHY;
        }
        return Result.NONE;
    }
}
