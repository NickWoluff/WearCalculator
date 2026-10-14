package com.nickwoluff.wearcalculator;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.lifecycle.Lifecycle;

/** 更新逻辑由 Java 管理，界面使用官方 Wear Material 3 组件。 */
public class SettingsActivity extends WearActivity {
    private static final String RELEASES_URL = "https://github.com/NickWoluff/WearCalculator/releases";
    private String localVersion;
    private SettingsUiController ui;
    private ArcToastView toast;
    private final UpdateChecker checker = new UpdateChecker();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            localVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            throw new IllegalStateException(exception);
        }
        ui = new SettingsUiController(this, localVersion, this::checkForUpdate, this::openRelease,
                round -> {
                    if (LayoutPreferences.isRound(this) != round) {
                        LayoutPreferences.setRound(this, round);
                        recreate();
                    }
                }, this::openTheme);
        setContentView(ui.getView());
        toast = new ArcToastView(this);
        if (state != null && state.containsKey("releaseTag")) ui.showRelease(state.getString("releaseTag"));
    }

    private void openTheme() {
        startActivity(new Intent(this, ThemeActivity.class));
        if (Build.VERSION.SDK_INT < 34) {
            overridePendingTransition(R.anim.settings_enter, R.anim.page_hold);
        }
    }

    private void checkForUpdate() {
        ui.updateChecking(true);
        checker.check(new UpdateChecker.Callback() {
            @Override
            public void onResult(String tagName, String releaseUrl) {
                if (isFinishing() || isDestroyed()) return;
                ui.updateChecking(false);
                try {
                    if (VersionComparator.isNewer(tagName, localVersion)) ui.showRelease(tagName);
                    else showNotice(R.string.already_latest);
                } catch (IllegalArgumentException exception) {
                    showNotice(R.string.update_failed);
                }
            }

            @Override
            public void onError(int statusCode) {
                if (isFinishing() || isDestroyed()) return;
                ui.updateChecking(false);
                int message = statusCode == 403 || statusCode == 429
                        ? R.string.update_rate_limited : statusCode == 404
                        ? R.string.no_releases : R.string.update_failed;
                showNotice(message);
            }
        });
    }

    private void openRelease() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)));
        } catch (ActivityNotFoundException exception) {
            showNotice(R.string.no_browser);
        }
    }

    private void showNotice(int message) {
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            toast.show(getString(message));
        }
    }

    SettingsUiController getUiForTesting() {
        return ui;
    }

    boolean canSwipeDownToExit() {
        return ui != null && ui.isAtListStart();
    }

    ArcToastView getToastForTesting() {
        return toast;
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (ui.getReleaseTag() != null) state.putString("releaseTag", ui.getReleaseTag());
    }

    @Override
    protected void onDestroy() {
        checker.close();
        if (toast != null) toast.dismiss();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        if (toast != null) toast.dismiss();
        super.onStop();
    }
}
