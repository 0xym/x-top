package com.example.oxym.test2;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Icon;
import android.os.IBinder;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Vector;

/**
 * Created by oxym on 14.05.16.
 */
public class NotificationUpdate extends Service {

    private static final String KEY_NOTIFICATION_ENABLED = "notification_enabled";

    private class ProcStat
    {
        public String name;
        public long user;
        public long nice;
        public long system;
        public long idle;
        public long iowait;
        public long irq;
        public long swirq;
        public long rest;

        public long active() {return user + nice + system + iowait + irq + swirq + rest; }
        public long total()
        {
            return idle + active();
        }
    }

    Vector<ProcStat> lastMeasurement;

    private Vector<ProcStat> readCpuStat() {
        Vector<ProcStat> retVal = new Vector<ProcStat>();
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            while(true) {
                String load = reader.readLine();

                String[] toks = load.split(" +");  // Split on one or more spaces
                if (!toks[0].matches("cpu.*")) {
                    break;
                }
                ProcStat stat = new ProcStat();

                stat.name = toks[0];
                stat.user = Long.parseLong(toks[1]);
                stat.nice = Long.parseLong(toks[2]);
                stat.system = Long.parseLong(toks[3]);
                stat.idle = Long.parseLong(toks[4]);
                stat.iowait = Long.parseLong(toks[5]);
                stat.irq = Long.parseLong(toks[6]);
                stat.swirq = Long.parseLong(toks[7]);
                for (int i = 8; i < toks.length; ++i) {
                    stat.rest += Long.parseLong(toks[i]);
                }
                retVal.add(stat);
            }
            reader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return retVal;
    }

    class CpuStat {
        public String name;
        public float usage;
        public float iowait;
    }

    private float readAndUpdateUsage() {
        Vector<CpuStat> retVal = new Vector<CpuStat>();
        Vector<ProcStat> newStat = readCpuStat();
        for (int i = 0; i < newStat.size(); ++i)
        {
            if ((lastMeasurement.size() > i) && (lastMeasurement.get(i).name.equals(newStat.get(i).name)))
            {
                CpuStat result = new CpuStat();
                result.name = newStat.get(i).name;
                result.usage = (float) (newStat.get(i).active() - lastMeasurement.get(i).active()) / (newStat.get(i).total() - lastMeasurement.get(i).total());
                result.iowait = (float) (newStat.get(0).iowait - lastMeasurement.get(0).iowait) / (newStat.get(0).total() - lastMeasurement.get(0).total());
            }//else: try hard
        }
        float result = (float) (newStat.get(0).active() - lastMeasurement.get(0).active()) / (newStat.get(0).total() - lastMeasurement.get(0).total());
        lastMeasurement = newStat;
        return result;
    }

    Notification.Builder mBuilder;
    int[] bars;
    int lastBar;
    final int iconSize = 56;
    final int iconBorder = 0;
    final int drawableIconSize = iconSize - 2 * iconBorder;
    boolean mInitialized = false;
    boolean mRunning = false;

    protected Icon getCurrentIcon() {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        Bitmap bg = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bg);
        for (int i = 0; i < drawableIconSize; ++i) {
            int index = (lastBar + drawableIconSize - i) % drawableIconSize;
            canvas.drawRect(iconBorder + drawableIconSize - i - 1, iconBorder + drawableIconSize - bars[index], iconBorder + drawableIconSize - i, iconBorder + drawableIconSize, paint);
        }
        return Icon.createWithBitmap(bg);
    }

    final int mNotificationId = 001;

    protected void init() {
        mInitialized = true;
        bars = new int[drawableIconSize];
        for (int i = 0; i < drawableIconSize; ++i) {
            bars[i] = 0;
        }
        lastBar = 0;
        mBuilder = new Notification.Builder(this)
                .setSmallIcon(getCurrentIcon())
                .setContentTitle("X-Top");
        Notification notification = new Notification();
        startForeground(mNotificationId, notification);

        new Thread(
                new Runnable() {
                    @Override
                    public void run() {

                        int mNotificationId = 001;
                        NotificationManager mNotifyMgr =
                                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                        mNotifyMgr.notify(mNotificationId, mBuilder.build());
                        lastMeasurement = readCpuStat();
                        for (; mRunning; ) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            Float usage = readAndUpdateUsage();
                            lastBar = (lastBar + 1) % drawableIconSize;
                            bars[lastBar] = (int) (usage * drawableIconSize + 0.4D);
                            if (!mRunning)
                            {
                                break;
                            }
                            usage *= 100;
                            String text = "CPU usage: " + usage.toString() + "%";
                            mBuilder.setContentText(text).setSmallIcon(getCurrentIcon());
                            mNotifyMgr.notify(mNotificationId, mBuilder.build());

                        }
                        stopForeground(true);
                        mNotifyMgr.cancel(mNotificationId); //or just cancel all
                    }
                }
// Starts the thread by calling the run() method in its Runnable
        ).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        mRunning = true;
        if (!mInitialized) {
            init();
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRunning = false;
        //Sleep?
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

}
