package com.huantansheng.easyphotos.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.huantansheng.easyphotos.R;
import com.huantansheng.easyphotos.ui.EasyPhotosActivity;

import java.util.UUID;

/**
 * 拍照前台进程，防止应用在后台被回收
 *
 * @author lh
 */
public class BackgroundCallService extends Service {
    public static final int NOTIFICATION_ONGOING_ID = 20;
    private static final String CHANNEL_ID = "camera_channel";
    private static final String CHANNEL_NAME = "相机";
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showInCallNotification();
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideInCallNotification();
    }

    private void showInCallNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ONGOING_ID, getInCallNotification());
        } else {
            notificationManager.notify(NOTIFICATION_ONGOING_ID, getInCallNotification());
        }
    }

    private void hideInCallNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        } else {
            notificationManager.cancel(NOTIFICATION_ONGOING_ID);
        }
    }

    public Notification getInCallNotification() {
        Intent intent = new Intent(getApplicationContext(), EasyPhotosActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                UUID.randomUUID().hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), getInCallChannelId())
                .setSmallIcon(R.drawable.ic_camera_easy_photos)
                .setDefaults(NotificationCompat.FLAG_ONGOING_EVENT)
                .setSound(null)
                .setVibrate(new long[]{0})
                .setContentTitle("相机")
                .setContentText("正在运行")
                .setContentIntent(pendingIntent);
        return builder.build();
    }

    public String getInCallChannelId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("正在运行");
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setVibrationPattern(new long[]{0});
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
        }
        return CHANNEL_ID;
    }
}
