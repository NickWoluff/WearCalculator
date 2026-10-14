package com.nickwoluff.wearcalculator;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/** 每次首页启动仅检查一次；页面重建时保留请求及新版本提示。 */
public final class StartupUpdateViewModel extends ViewModel {
    interface Request {
        void check(UpdateChecker.Callback callback);
    }

    private final Request request;
    private final Runnable close;
    private final MutableLiveData<String> availableVersion = new MutableLiveData<>();
    private boolean started;
    private boolean cleared;

    public StartupUpdateViewModel() {
        UpdateChecker checker = new UpdateChecker();
        request = checker::check;
        close = checker::close;
    }

    StartupUpdateViewModel(Request request, Runnable close) {
        this.request = request;
        this.close = close;
    }

    public LiveData<String> getAvailableVersion() { return availableVersion; }

    public void dismiss() { availableVersion.setValue(null); }

    void checkOnce(Context context, String localVersion) {
        if (started || cleared) return;
        started = true;
        if (!UpdatePreferences.autoCheck(context)) return;
        Context application = context.getApplicationContext();
        request.check(new UpdateChecker.Callback() {
            @Override public void onResult(String tagName, String releaseUrl) {
                if (cleared || !UpdatePreferences.autoCheck(application)) return;
                try {
                    if (VersionComparator.isNewer(tagName, localVersion)) availableVersion.setValue(tagName);
                } catch (IllegalArgumentException ignored) {
                    // 与 Credex 一致，自动检查无更新或失败时不打扰用户。
                }
            }

            @Override public void onError(int statusCode) { }
        });
    }

    @Override protected void onCleared() {
        cleared = true;
        close.run();
    }
}
