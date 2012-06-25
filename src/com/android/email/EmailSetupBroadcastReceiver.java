package com.android.email;

import com.android.email.service.AccountCreationService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class EmailSetupBroadcastReceiver extends BroadcastReceiver{

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("EmailSetupBroadcastReceiver", "Intent received: " + intent.toString());
        intent.setClass(context, AccountCreationService.class);
        context.startService(intent);
    }

}
