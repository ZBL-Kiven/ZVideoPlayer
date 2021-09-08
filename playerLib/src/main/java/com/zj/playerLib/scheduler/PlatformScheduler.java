//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.scheduler;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobInfo.Builder;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;

import androidx.annotation.RequiresPermission;

import com.zj.playerLib.util.Util;

@TargetApi(21)
public final class PlatformScheduler implements Scheduler {
    private static final String TAG = "PlatformScheduler";
    private static final String KEY_SERVICE_ACTION = "service_action";
    private static final String KEY_SERVICE_PACKAGE = "service_package";
    private static final String KEY_REQUIREMENTS = "requirements";
    private final int jobId;
    private final ComponentName jobServiceComponentName;
    private final JobScheduler jobScheduler;

    @RequiresPermission("android.permission.RECEIVE_BOOT_COMPLETED")
    public PlatformScheduler(Context context, int jobId) {
        this.jobId = jobId;
        this.jobServiceComponentName = new ComponentName(context, PlatformSchedulerService.class);
        this.jobScheduler = (JobScheduler)context.getSystemService("jobscheduler");
    }

    public boolean schedule(Requirements requirements, String servicePackage, String serviceAction) {
        JobInfo jobInfo = buildJobInfo(this.jobId, this.jobServiceComponentName, requirements, serviceAction, servicePackage);
        int result = this.jobScheduler.schedule(jobInfo);
        logd("Scheduling job: " + this.jobId + " result: " + result);
        return result == 1;
    }

    public boolean cancel() {
        logd("Canceling job: " + this.jobId);
        this.jobScheduler.cancel(this.jobId);
        return true;
    }

    private static JobInfo buildJobInfo(int jobId, ComponentName jobServiceComponentName, Requirements requirements, String serviceAction, String servicePackage) {
        Builder builder = new Builder(jobId, jobServiceComponentName);
        byte networkType;
        switch(requirements.getRequiredNetworkType()) {
        case 0:
            networkType = 0;
            break;
        case 1:
            networkType = 1;
            break;
        case 2:
            networkType = 2;
            break;
        case 3:
            if (Util.SDK_INT < 24) {
                throw new UnsupportedOperationException();
            }

            networkType = 3;
            break;
        case 4:
            if (Util.SDK_INT < 26) {
                throw new UnsupportedOperationException();
            }

            networkType = 4;
            break;
        default:
            throw new UnsupportedOperationException();
        }

        builder.setRequiredNetworkType(networkType);
        builder.setRequiresDeviceIdle(requirements.isIdleRequired());
        builder.setRequiresCharging(requirements.isChargingRequired());
        builder.setPersisted(true);
        PersistableBundle extras = new PersistableBundle();
        extras.putString("service_action", serviceAction);
        extras.putString("service_package", servicePackage);
        extras.putInt("requirements", requirements.getRequirementsData());
        builder.setExtras(extras);
        return builder.build();
    }

    private static void logd(String message) {
    }

    public static final class PlatformSchedulerService extends JobService {
        public PlatformSchedulerService() {
        }

        public boolean onStartJob(JobParameters params) {
            PlatformScheduler.logd("PlatformSchedulerService started");
            PersistableBundle extras = params.getExtras();
            Requirements requirements = new Requirements(extras.getInt("requirements"));
            if (requirements.checkRequirements(this)) {
                PlatformScheduler.logd("Requirements are met");
                String serviceAction = extras.getString("service_action");
                String servicePackage = extras.getString("service_package");
                Intent intent = (new Intent(serviceAction)).setPackage(servicePackage);
                PlatformScheduler.logd("Starting service action: " + serviceAction + " package: " + servicePackage);
                Util.startForegroundService(this, intent);
            } else {
                PlatformScheduler.logd("Requirements are not met");
                this.jobFinished(params, true);
            }

            return false;
        }

        public boolean onStopJob(JobParameters params) {
            return false;
        }
    }
}
