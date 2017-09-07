package org.ligi.fast;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ChangedPackages;
import android.os.Build;

import org.ligi.fast.background.BackgroundGatherAsyncTask;
import org.ligi.fast.background.ChangedPackagesAsyncTask;
import org.ligi.fast.model.AppInfoList;
import org.ligi.fast.settings.AndroidFASTSettings;
import org.ligi.fast.settings.FASTSettings;
import org.ligi.tracedroid.TraceDroid;

import java.io.File;
import java.lang.ref.WeakReference;

public class App extends Application {

    private static FASTSettings settings;
    private static App appInstance;

    public static final String LOG_TAG = "FAST App Search";

    public interface PackageChangedListener {
        public void onPackageChange(AppInfoList appInfoList);
    }

    public static PackageChangedListener packageChangedListener;
    public static WeakReference<AppInfoList> backingAppInfoList;

    @Override
    public void onCreate() {
        super.onCreate();
        appInstance = this;
        TraceDroid.init(this);
        settings = new AndroidFASTSettings(App.this);

        // Since the BroadCastReceiver can't run on Android 8 Oreo
        // Get boot count and sequence number and update the App List
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int lastKnownSequenceNumber = settings.getSequenceNumber();
            if (lastKnownSequenceNumber == settings.DEFAULT_KNOWN_PM_SEQUENCE_NUMBER) {
                // First after boot
                // Since changes between last app shutdown and device shutdown
                // could be possible there's a full refresh needed.
                settings.putSequenceNumber(0);
                new BackgroundGatherAsyncTask(getApplicationContext()).execute();
            } else {
                ChangedPackages changedPackages =
                        getApplicationContext().getPackageManager()
                                .getChangedPackages(lastKnownSequenceNumber);
                if (changedPackages != null) {
                    settings.putSequenceNumber(changedPackages.getSequenceNumber());
                    new ChangedPackagesAsyncTask(getApplicationContext(), changedPackages).execute();
                }
            }
        }
    }

    public static FASTSettings getSettings() {
        return settings;
    }


    private static int getThemeByString(String theme) {

        switch (theme) {
            case "transparent":
                return R.style.transparent_dark;

            case "transparent_light":
                return R.style.transparent_light;

            case "dark":
                return R.style.dark;

            case "light":
            default:
                return R.style.light;

        }
    }

    public static void injectSettingsForTesting(FASTSettings newSettings) {
        settings = newSettings;
    }

    public static void applyTheme(Activity activity) {
        applyTheme(activity, getSettings().getTheme());
    }

    public static void applyTheme(Activity activity, final String theme) {
        activity.setTheme(getThemeByString(theme));
    }

    public static String getStoreURL4PackageName(String pname) {
        return TargetStore.STORE_URL + pname;
    }

    public static File getBaseDir() {
        return appInstance.getFilesDir();
    }
}
