package com.example.oxym.test2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

/**
 * Created by oxym on 14.05.16.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent serviceIntent = new Intent(context, NotificationUpdate.class);
            boolean running =  PreferenceManager.getDefaultSharedPreferences(context).
                    getBoolean(MyActivity.KEY_NOTIFICATION_ENABLED, true);
            if (running)
            {
                context.startService(serviceIntent);
            }
        }
    }
}