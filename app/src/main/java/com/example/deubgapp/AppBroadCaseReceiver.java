package com.example.deubgapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class AppBroadCaseReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(context, "HelloWorld  " + intent.toString(), Toast.LENGTH_LONG).show();
    }
}
