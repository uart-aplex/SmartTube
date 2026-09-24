package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.graphics.Color;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AddDevicePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.sharedutils.misc.WeakHashSet;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreensaverManager {
    private static final String TAG = ScreensaverManager.class.getSimpleName();
    private static final int MODE_SCREENSAVER = 0;
    private static final int MODE_SCREEN_OFF = 1;
    private static final long CLOCK_UPDATE_MS = 15_000;
    private static final long POSITION_UPDATE_MS = 3 * 60 * 1_000L;
    private static final WeakHashSet<ScreensaverManager> sInstances = new WeakHashSet<>();
    private static boolean sLockInstance;
    private final WeakReference<Activity> mActivity;
    private final WeakReference<View> mDimContainer;
    private final Runnable mDimScreen = this::dimScreen;
    private final Runnable mUndimScreen = this::undimScreen;
    private final Runnable mUnlockInstance = () -> sLockInstance = false;
    private final Runnable mUpdateClock = this::updateClock;
    private final Runnable mMoveInfo = this::moveInfo;
    private int mMode = MODE_SCREENSAVER;
    private boolean mIsScreenOff;
    private boolean mIsInfoVisible;
    private int mWakeKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private int mInfoPosition;
    private boolean mIsBlocked;
    // Distinct from mIsBlocked: lifecycle suspension stops all screensaver management while
    // the host activity is not in the foreground. Blocked only suppresses user-facing dimming.
    private boolean mIsSuspended;
    private final Runnable mTimeoutHandler = () -> {
        // Playing the video and dialog overlay isn't shown
        if (getViewManager().getTopView() != PlaybackView.class || !getTweaksData().isScreenOffTimeoutEnabled()) {
            return;
        }

        if (!getAppDialogPresenter().isDialogShown()) {
            doScreenOff();
        } else {
            // showing dialog... or recheck...
            enableTimeout();
        }
    };

    public ScreensaverManager(Activity activity) {
        mActivity = new WeakReference<>(activity);
        mDimContainer = new WeakReference<>(createDimContainer(activity));
        enable();
        addToRegistry();
    }

    private View createDimContainer(Activity activity) {
        View rootView = activity.getWindow().getDecorView().getRootView();

        View dimContainer = rootView.findViewById(R.id.dim_container);

        if (dimContainer == null) {
            LayoutInflater layoutInflater = activity.getLayoutInflater();
            dimContainer = layoutInflater.inflate(R.layout.dim_container, null);
            if (rootView instanceof ViewGroup) {
                // NOTE: zoom will be bugged! Frames on top and bottom.
                // Add negative margin to fix un-proper viewport positioning on some devices
                // NOTE: below code is not working!!!
                // NOTE: comment out code below if you don't want this
                //LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                //        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                //params.setMargins(-30, -30, -30, -30);
                //((ViewGroup) rootView).addView(dimContainer, params);

                ((ViewGroup) rootView).addView(dimContainer);
            }
        }

        return dimContainer;
    }

    /**
     * Screen off check
     */
    public void enableChecked() {
        // Fix dialog dimming when using the play button on the remote controller.
        // NOTE: only the last activity will show dimming and in our case the last one is PlaybackActivity
        if (mMode == MODE_SCREEN_OFF || mIsInfoVisible || getAppDialogPresenter().isDialogShown()) {
            return;
        }

        enable();
    }

    /**
     * Screen off check
     */
    public void disableChecked() {
        if (mMode == MODE_SCREEN_OFF) {
            return;
        }

        disable();
    }

    public void enable() {
        if (mIsSuspended) {
            return;
        }

        if (mIsBlocked) {
            Log.d(TAG, "Screensaver blocked!");
            return;
        }

        Log.d(TAG, "Enable screensaver");

        disable();
        int delayMs = getGeneralData().getScreensaverTimeoutMs() == GeneralData.SCREENSAVER_TIMEOUT_NEVER ?
                10_000 :
                getGeneralData().getScreensaverTimeoutMs();
        Utils.postDelayed(mDimScreen, delayMs);
    }

    public void disable() {
        if (mIsSuspended) {
            return;
        }

        if (mIsBlocked) {
            Log.d(TAG, "Screensaver blocked!");
            return;
        }

        Log.d(TAG, "Disable screensaver");
        mMode = MODE_SCREENSAVER;
        Utils.removeCallbacks(mDimScreen);
        Utils.postDelayed(mUndimScreen, 0);
    }

    public void doScreenOff() {
        // Ignore suspend if dialog is opened to apply settings immediately
        if (mIsSuspended && !getAppDialogPresenter().isDialogShown()) {
            return;
        }

        // NOTE: disable will create infinite loop
        //disable();
        mMode = MODE_SCREEN_OFF;
        Utils.removeCallbacks(mUndimScreen);
        Utils.postDelayed(mDimScreen, 0);
    }

    public boolean isScreenOff() {
        return mIsScreenOff;
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == mWakeKeyCode) {
            mWakeKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN && mIsInfoVisible) {
            mWakeKeyCode = event.getKeyCode();
            enable();
            return true;
        }

        return false;
    }

    /**
     * Stop managing the screensaver while the host activity is not in the foreground.
     * Distinct from {@link #setBlocked(boolean)}: blocked only suppresses user-facing dimming,
     * while suspension always releases wake suppression and ignores later playback events.
     */
    public void suspend() {
        mIsSuspended = true;
        // Leave mUnlockInstance queued so the shared registry lock cannot be stranded.
        Utils.removeCallbacks(mDimScreen, mUndimScreen, mTimeoutHandler);
        if (!mIsBlocked) {
            hideDimOverlay();
        }
        enableSystemScreensaver();
    }

    /**
     * Resume the existing dimming policy after the host activity returns to the foreground.
     */
    public void resume() {
        mIsSuspended = false;
        enable();
        if (mIsBlocked) {
            disableSystemScreensaver();
        }
    }

    /**
     * Idempotent cleanup for activity destruction. Releases suppression and drops this instance
     * from the shared registry without cancelling an in-flight registry unlock.
     */
    public void cleanup() {
        suspend();
        sInstances.remove(this);
    }

    public void setBlocked(boolean blocked) {
        mIsBlocked = blocked;
    }

    private void enableTimeout() {
        // Playing the video and dialog overlay isn't shown
        if (getViewManager().getTopView() != PlaybackView.class || !getTweaksData().isScreenOffTimeoutEnabled()) {
            disableTimeout();
            return;
        }

        Log.d(TAG, "Starting auto hide ui timer...");
        disableTimeout();
        Utils.postDelayed(mTimeoutHandler, getTweaksData().getScreenOffTimeoutSec() * 1_000L);
    }

    private void disableTimeout() {
        Log.d(TAG, "Stopping auto hide ui timer...");
        Utils.removeCallbacks(mTimeoutHandler);
    }

    private void dimScreen() {
        showHide(true);
    }

    private void undimScreen() {
        showHide(false);
    }

    private void showHide(boolean show) {
        showHideScreensaver(show);
        showHideDimming(show);
    }

    private void showHideDimming(boolean show) {
        Activity activity = mActivity.get();
        View dimContainer = mDimContainer.get();

        if (activity == null || dimContainer == null) {
            return;
        }

        if (!show) {
            enableTimeout();
        }

        // Disable dimming on certain circumstances
        if (show && mMode == MODE_SCREENSAVER &&
                (       isPlaying() ||
                        isSigning() ||
                        getGeneralData().getScreensaverTimeoutMs() == GeneralData.SCREENSAVER_TIMEOUT_NEVER
                )
        ) {
            return;
        }

        int screenOffColor = Utils.getColor(activity, R.color.black, getTweaksData().getScreenOffDimmingPercents());
        //int screenOffColorResId = getPlayerTweaksData().getScreenOffDimmingPercents() == 50 ? DIM_50 : DIM_100;
        dimContainer.setBackgroundColor(mMode == MODE_SCREENSAVER ? Color.BLACK : screenOffColor);
        //dimContainer.setBackgroundResource(mMode == MODE_SCREENSAVER ? screensaverColorResId : screenOffColorResId);
        dimContainer.setVisibility(show ? View.VISIBLE : View.GONE);

        View info = dimContainer.findViewById(R.id.screensaver_info);
        mIsInfoVisible = show && mMode == MODE_SCREENSAVER;
        info.setVisibility(mIsInfoVisible ? View.VISIBLE : View.GONE);

        if (mIsInfoVisible) {
            startInfoScreen(activity, dimContainer);
        } else {
            stopInfoScreen();
        }

        mIsScreenOff = mMode == MODE_SCREEN_OFF && getTweaksData().getScreenOffDimmingPercents() == 100 && show;

        if (mIsScreenOff) {
            hidePlayerOverlay();
        }

        notifyRegistry();
    }

    private void showHideScreensaver(boolean show) {
        Activity activity = mActivity.get();

        if (activity == null) {
            return;
        }

        if (sLockInstance) {
            Helpers.enableScreensaver(activity);
            return;
        }

        if (show && mMode == MODE_SCREENSAVER) {
            Helpers.disableScreensaver(activity);
            return;
        }

        // Disable screensaver on certain circumstances
        // Fix screen off before the video started
        if (show && (isPlaying() || isSigning() || getGeneralData().isScreensaverDisabled() || (mMode == MODE_SCREEN_OFF && getPosition() == 0))) {
            Helpers.disableScreensaver(activity);
            return;
        }

        if (show) {
            Helpers.enableScreensaver(activity);
        } else {
            Helpers.disableScreensaver(activity);
        }
    }

    private boolean isPlaying() {
        Activity activity = mActivity.get();

        if (activity == null) {
            return false;
        }

        PlaybackView playbackView = PlaybackPresenter.instance(activity).getView();
        return playbackView != null && playbackView.isPlaying();
    }

    private void startInfoScreen(Activity activity, View dimContainer) {
        updateClock();
        moveInfo();

        TextView weatherView = dimContainer.findViewById(R.id.screensaver_weather);
        TextView rainView = dimContainer.findViewById(R.id.screensaver_rain);
        weatherView.setText(R.string.screensaver_weather_loading);
        rainView.setText("");

        WeatherService.load(getGeneralData().getScreensaverWeatherLocation(), new WeatherService.Listener() {
            @Override
            public void onResult(WeatherService.WeatherResult result) {
                Utils.post(() -> {
                    if (!mIsInfoVisible) {
                        return;
                    }
                    weatherView.setText(activity.getString(R.string.screensaver_weather_format,
                            getWeatherDescription(result.weatherCode), result.temperature));
                    rainView.setText(activity.getString(R.string.screensaver_rain_format, result.rainProbability));
                });
            }

            @Override
            public void onError() {
                Utils.post(() -> {
                    if (mIsInfoVisible) {
                        weatherView.setText(R.string.screensaver_weather_unavailable);
                        rainView.setText("");
                    }
                });
            }
        });

        if (getGeneralData().isScreensaverPlaybackPaused() && isPlaying()) {
            PlaybackView playbackView = PlaybackPresenter.instance(activity).getView();
            if (playbackView != null) {
                playbackView.setPlayWhenReady(false);
            }
        }
    }

    private void stopInfoScreen() {
        Utils.removeCallbacks(mUpdateClock);
        Utils.removeCallbacks(mMoveInfo);
    }

    private void updateClock() {
        Activity activity = mActivity.get();
        View container = mDimContainer.get();
        if (!mIsInfoVisible || activity == null || container == null) {
            return;
        }

        Locale locale = activity.getResources().getConfiguration().locale;
        String timePattern = DateFormat.is24HourFormat(activity) ? "HH:mm" : "h:mm";
        Date now = new Date();
        ((TextView) container.findViewById(R.id.screensaver_time)).setText(new SimpleDateFormat(timePattern, locale).format(now));
        ((TextView) container.findViewById(R.id.screensaver_date)).setText(new SimpleDateFormat("yyyy/MM/dd EEEE", locale).format(now));
        Utils.removeCallbacks(mUpdateClock);
        Utils.postDelayed(mUpdateClock, CLOCK_UPDATE_MS);
    }

    private void moveInfo() {
        Activity activity = mActivity.get();
        View container = mDimContainer.get();
        if (!mIsInfoVisible || activity == null || container == null) {
            return;
        }

        float offset = 36 * activity.getResources().getDisplayMetrics().density;
        float[][] positions = {{0, 0}, {-offset, -offset}, {offset, -offset}, {offset, offset}, {-offset, offset}};
        View info = container.findViewById(R.id.screensaver_info);
        mInfoPosition = (mInfoPosition + 1) % positions.length;
        info.animate().translationX(positions[mInfoPosition][0]).translationY(positions[mInfoPosition][1]).setDuration(800).start();
        Utils.removeCallbacks(mMoveInfo);
        Utils.postDelayed(mMoveInfo, POSITION_UPDATE_MS);
    }

    private String getWeatherDescription(int code) {
        Activity activity = mActivity.get();
        if (activity == null) {
            return "";
        }
        if (code == 0) return activity.getString(R.string.weather_clear);
        if (code <= 3) return activity.getString(R.string.weather_cloudy);
        if (code == 45 || code == 48) return activity.getString(R.string.weather_fog);
        if (code >= 51 && code <= 57) return activity.getString(R.string.weather_drizzle);
        if (code >= 61 && code <= 67) return activity.getString(R.string.weather_rain);
        if (code >= 71 && code <= 77) return activity.getString(R.string.weather_snow);
        if (code >= 80 && code <= 82) return activity.getString(R.string.weather_showers);
        if (code >= 85 && code <= 86) return activity.getString(R.string.weather_snow_showers);
        if (code >= 95) return activity.getString(R.string.weather_thunderstorm);
        return activity.getString(R.string.weather_cloudy);
    }

    private long getPosition() {
        Activity activity = mActivity.get();

        if (activity == null) {
            return 0;
        }

        PlaybackView playbackView = PlaybackPresenter.instance(activity).getView();
        // Fix screen off before the video started
        return playbackView != null ? playbackView.getPositionMs() : 0;
    }

    private boolean isSigning() {
        Activity activity = mActivity.get();

        if (activity == null) {
            return false;
        }

        return SignInPresenter.instance(activity).getView() != null || AddDevicePresenter.instance(activity).getView() != null;
    }

    private void hidePlayerOverlay() {
        Activity activity = mActivity.get();

        if (activity == null) {
            return;
        }

        PlaybackView playbackView = PlaybackPresenter.instance(activity).getView();

        if (playbackView != null) {
            playbackView.showOverlay(false);
        }
    }

    /**
     * Hide the dim overlay without going through {@link #undimScreen()}, which would
     * reacquire wake suppression via {@link Helpers#disableScreensaver(Activity)}.
     */
    private void hideDimOverlay() {
        View dimContainer = mDimContainer.get();

        if (dimContainer != null) {
            dimContainer.setVisibility(View.GONE);
            View info = dimContainer.findViewById(R.id.screensaver_info);
            if (info != null) {
                info.setVisibility(View.GONE);
            }
        }

        mIsInfoVisible = false;
        mIsScreenOff = false;
        stopInfoScreen();
    }

    private void enableSystemScreensaver() {
        Activity activity = mActivity.get();

        if (activity != null) {
            Helpers.enableScreensaver(activity);
        }
    }

    private void disableSystemScreensaver() {
        Activity activity = mActivity.get();

        if (activity != null) {
            Helpers.disableScreensaver(activity);
        }
    }

    private void addToRegistry() {
        sInstances.add(this);
    }

    private void notifyRegistry() {
        if (mIsSuspended || sLockInstance) {
            return;
        }

        sLockInstance = true;

        sInstances.forEach(item -> {
            if (item != this) {
                item.disableChecked();
            }
        });

        Utils.postDelayed(mUnlockInstance, 0);
    }

    private AppDialogPresenter getAppDialogPresenter() {
        return AppDialogPresenter.instance(mActivity.get());
    }

    private ViewManager getViewManager() {
        return ViewManager.instance(mActivity.get());
    }

    private PlayerTweaksData getTweaksData() {
        return PlayerTweaksData.instance(mActivity.get());
    }

    private GeneralData getGeneralData() {
        return GeneralData.instance(mActivity.get());
    }
}
