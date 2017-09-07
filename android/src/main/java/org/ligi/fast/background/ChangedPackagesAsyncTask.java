package org.ligi.fast.background;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ChangedPackages;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.util.Log;

import org.ligi.fast.App;
import org.ligi.fast.model.AppInfo;
import org.ligi.fast.model.AppInfoList;
import org.ligi.fast.util.AppInfoListStore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@TargetApi(26)
public class ChangedPackagesAsyncTask extends AsyncTask<Void, Void, Void> {
    private Context mContext;
    private List<String> mChangedPackageNames;
    private AppInfoListStore mAppInfoListStore;

    private void save(AppInfoList appInfoList) {
        if (App.packageChangedListener == null) {
            mAppInfoListStore.save(appInfoList);
        } else {
            App.packageChangedListener.onPackageChange(appInfoList);
        }
    }

    public ChangedPackagesAsyncTask(Context context, List<String> changedPackages) {
        this.mContext = context;
        this.mChangedPackageNames = changedPackages;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        if (mChangedPackageNames == null) return null; // Promised to never be the case
        if (mChangedPackageNames.size() == 0) return null;
        long start = System.currentTimeMillis();

        mAppInfoListStore = new AppInfoListStore(mContext);
        AppInfoList appInfoList = null;
        if (App.backingAppInfoList != null) {
            appInfoList = App.backingAppInfoList.get();
        }
        if (appInfoList == null) {
            appInfoList = mAppInfoListStore.load();
        }

        AppInfoList matchedAppInfoList = new AppInfoList();
        // Collect the existing records already held about this app
        // into matchedAppInfoList and remove them from the main list.
        // That way if uninstalling an app the main list is already updated after this step.
        // If it is an update only matchedAppInfoList will have to be iterated in the next step
        // since it contains all records that need an update.
        // After this all old info & icons of the affected package should be cleaned up.
        for (String packageName : mChangedPackageNames) {
            Log.d(App.LOG_TAG, "changed: " + packageName);
            for (Iterator<AppInfo> iterator = appInfoList.iterator(); iterator.hasNext(); ) {
                AppInfo appInfo = iterator.next();
                if (appInfo.getPackageName().equals(packageName)) {
                    matchedAppInfoList.add(appInfo);
                    iterator.remove();
                }
            }
        }

        List<ResolveInfo> resolveInfoList = new ArrayList<>();
        List<ResolveInfo> homeInfoList = new ArrayList<>();
        for (String packageName : mChangedPackageNames) {
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launcherIntent.setPackage(packageName);
            resolveInfoList.addAll(mContext.getPackageManager().queryIntentActivities(launcherIntent, 0));

            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setPackage(packageName);
            homeInfoList.addAll(mContext.getPackageManager().queryIntentActivities(homeIntent, 0));
        }

        // If there are no activities that should be displayed on the launcher we can quit here
        if (resolveInfoList.size() == 0 && homeInfoList.size() == 0) {
            save(appInfoList);
            return null;
        }

        // Deduplicate Resolve Info of activities with both categories - like SearchActivity (see manifest)
        for (ResolveInfo info : resolveInfoList) {
            Iterator<ResolveInfo> homeIterator = homeInfoList.iterator();
            while (homeIterator.hasNext()) {
                ResolveInfo homeInfo = homeIterator.next();
                if (homeInfo.activityInfo.name.equals(info.activityInfo.name)) {
                    homeIterator.remove();
                    break;
                }
            }
            if (!homeIterator.hasNext()) {
                break;
            }
        }
        resolveInfoList.addAll(homeInfoList);

        if (matchedAppInfoList.size() == 0) { // New app, simple adding
            for (ResolveInfo info : resolveInfoList) {
                appInfoList.add(new AppInfo(mContext, info));
            }
        } else { // Update, merge data
            for (ResolveInfo info : resolveInfoList) {
                AppInfo newAppInfo = new AppInfo(mContext, info);

                Iterator<AppInfo> oldInfoIterator = matchedAppInfoList.iterator();
                while (oldInfoIterator.hasNext()) {
                    AppInfo oldInfo = oldInfoIterator.next();
                    if (oldInfo.getActivityName().equals(newAppInfo.getActivityName())) {
                        if (oldInfo.getLabelMode() == 2) { // AppInfo is alias
                            oldInfo.setLabel(newAppInfo.getLabel());
                            oldInfo.setInstallTime(newAppInfo.getInstallTime());
                            appInfoList.add(oldInfo);
                        } else {
                            newAppInfo.setCallCount(oldInfo.getCallCount());
                            newAppInfo.setPinMode(oldInfo.getPinMode());
                            newAppInfo.setLabelMode(oldInfo.getLabelMode());
                            newAppInfo.setOverrideLabel(oldInfo.getOverrideLabel());
                        }
                        oldInfoIterator.remove();
                    }
                }
                appInfoList.add(newAppInfo);
            }
        }

        save(appInfoList);
        mContext = null;
        long end = System.currentTimeMillis();
        long duration = end - start;
        Log.d(App.LOG_TAG, "ChangedPackages AsyncTask ran " + duration + "ms.");
        return null;
    }
}
