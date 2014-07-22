/******************************************************************************
 * Copyright (c) 2013-2014, AllSeen Alliance. All rights reserved.
 *
 *    Permission to use, copy, modify, and/or distribute this software for any
 *    purpose with or without fee is hereby granted, provided that the above
 *    copyright notice and this permission notice appear in all copies.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *    WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *    MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *    ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *    WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *    ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *    OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 ******************************************************************************/

package org.alljoyn.ioe.notificationviewer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.alljoyn.about.AboutService;
import org.alljoyn.about.AboutServiceImpl;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.Status;
import org.alljoyn.ioe.notificationviewer.logic.Interface.IntentActions;
import org.alljoyn.ioe.notificationviewer.properties.PropertiesManagerFactory;
import org.alljoyn.ns.Notification;
import org.alljoyn.ns.NotificationMessageType;
import org.alljoyn.ns.NotificationReceiver;
import org.alljoyn.ns.NotificationService;
import org.alljoyn.ns.NotificationServiceException;
import org.alljoyn.ns.NotificationText;
import org.alljoyn.services.common.utils.GenericLogger;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class NotificationViewer extends Service {
    /**
     * Max lines in toast
     */
    private static final int MAX_LINES = 4;

    final static String TAG = "TVNotificationViewer";

    /**
     * Reference to AboutClient
     */
    private AboutService aboutService;

    /**
     * Reference to NotificationService
     */
    private NotificationService notificationService;

    private NotificationManager m_androidNotificationManager;

    private AsyncHandler m_asyncHandler;

    private final String m_languageTag = "en";

    /* Load the native alljoyn_java library. */
    static {
        System.loadLibrary("alljoyn_java");
    }

    private static final String HTTP_PREFIX = "http";
    private static final String FTP_PREFIX = "ftp";

    /**
     * The {@link BusAttachment} to be used by the {@link NotificationService}
     */
    private BusAttachment bus;

    private final GenericLogger logger = new GenericLogger() {
        @Override
        public void debug(String TAG, String msg) {
            Log.d(TAG, msg);
        }

        @Override
        public void info(String TAG, String msg) {
            Log.i(TAG, msg);
        }

        @Override
        public void warn(String TAG, String msg) {
            Log.w(TAG, msg);
        }

        @Override
        public void error(String TAG, String msg) {
            Log.e(TAG, msg);
        }

        @Override
        public void fatal(String TAG, String msg) {
            Log.wtf(TAG, msg);
        }
    };

    private Handler handler;
    private WindowManager windowManager;
    private View rootNotificationsLayout;
    private ScrollView scrollView;
    private ViewGroup emergencyNotificationsLayout;
    private ViewGroup nonEmergencyNotificationsLayout;
    private BroadcastReceiver broadcastReceiver;

    private static final long POST_DELAYED_IN_MS_BEFORE_SCROLL = 500;

    public static final int IGNORE_VIEW_ID = -1;

    BusHandler busHandler;

    public NotificationViewer() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        PropertiesManagerFactory.getPropertiesManager().init();

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        rootNotificationsLayout = inflater.inflate(R.layout.notifications_layout, null);
        scrollView = (ScrollView) rootNotificationsLayout.findViewById(R.id.notificationScrollView);
        emergencyNotificationsLayout = (ViewGroup) rootNotificationsLayout.findViewById(R.id.emergencyNotificationLayout);
        nonEmergencyNotificationsLayout = (ViewGroup) rootNotificationsLayout.findViewById(R.id.notificationsLinearLayout);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSPARENT);

        windowManager.addView(rootNotificationsLayout, params);

        handler = new Handler(Looper.getMainLooper());

        m_androidNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        HandlerThread busThread = new HandlerThread("BusHandler");
        busThread.start();
        m_asyncHandler = new AsyncHandler(busThread.getLooper());
        m_asyncHandler.sendEmptyMessage(AsyncHandler.CONNECT);

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (IntentActions.ACTION_TVNOTIFICATION_VIEWER_ICON_AVAILABLE.equals(intent.getAction())) {
                    final UUID appId = (UUID) intent.getSerializableExtra(IntentActions.EXTRA_APP_ID);
                    if (null != appId) {
                        final boolean isNotificationWithImage = intent.getBooleanExtra(IntentActions.EXTRA_IS_NOTIFICATION_WITH_IMAGE, false);
                        if (isNotificationWithImage) {
                            final ImageView notificationIconImageView = (ImageView) emergencyNotificationsLayout
                                    .findViewById(R.id.emergency_notification_title_bar_about_image);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (null != notificationIconImageView) {
                                            UIUtil.setDeviceIcon(notificationIconImageView, appId, isNotificationWithImage, IGNORE_VIEW_ID);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                }
                            });
                        } else {
                            final int viewId = intent.getIntExtra(IntentActions.EXTRA_VIEW_ID, -1);
                            if (-1 != viewId) {
                                final View layout = nonEmergencyNotificationsLayout.findViewById(viewId);
                                if (null != layout) {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                View view = layout.findViewById(R.id.notification_toast_image);
                                                if (null != view) {
                                                    UIUtil.setDeviceIcon((ImageView) view, appId, isNotificationWithImage, viewId);
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(IntentActions.ACTION_TVNOTIFICATION_VIEWER_ICON_AVAILABLE);
        registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final int ID_RUN_CLOCK_IN_FOREGROUND = 1;
        android.app.Notification notification = new android.app.Notification.Builder(this)
                .setContentTitle(getResources().getString(R.string.app_name) + " running").setSmallIcon(R.drawable.tv_notify_regular).build();
        startForeground(ID_RUN_CLOCK_IN_FOREGROUND, notification);

        // Continue running until explicitly being stopped.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        /* Disconnect to prevent any resource leaks. */
        m_asyncHandler.shutdown();
        m_asyncHandler.getLooper().quit();

        if (null != rootNotificationsLayout) {
            windowManager.removeView(rootNotificationsLayout);
        }

        m_asyncHandler.sendEmptyMessage(AsyncHandler.DISCONNECT);

        unregisterReceiver(broadcastReceiver);
        super.onDestroy();
    }

    /* This class will handle all AllJoyn calls. See onCreate(). */
    class AsyncHandler extends Handler implements NotificationReceiver {

        public static final int CONNECT = 1;
        public static final int DISCONNECT = 2;
        public static final int NEW_NOTIFICATION = 3;
        public static final int SIMULATE = 4;
        private static final String SESSIONLESS_MATCH_RULE = "sessionless='t',type='error'";

        public AsyncHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

            /* Connect to the bus and start our service. */
            case CONNECT: {
                connect();
                break;
            }

            /* Release all resources acquired in connect. */
            case DISCONNECT: {
                disconnect();
                break;
            }

            case NEW_NOTIFICATION: {

                Notification notification = (Notification) msg.obj;
                // showNotification(notification);
                // showToast(notification);
                showNotificationNew(notification);
                break;
            }
            case SIMULATE: {
                String deviceName = "Simulator";
                String abcde = "abcdefghijklmn";
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < 15; i++) {
                    sb.append(" ").append(abcde);
                    doShowToast(-1, deviceName, sb);
                }
                break;
            }
            default:
                break;
            }
        }

        // ------------------------ Connect --------------------------------

        /**
         * Connect to the bus and start our services.
         */
        private void connect() {
            try {
                logger.info(TAG, "Initializing AllJoyn");
                prepareAJ();
            } catch (NotificationServiceException nse) {
                logger.error(TAG, "Failed to initialize AllJoyn, Error: '" + nse.getMessage() + "'");
                return;
            }

            /* Initialize AboutService - startAboutClient is now required for NotificationService */
            aboutService = AboutServiceImpl.getInstance();
            aboutService.setLogger(logger);
            try {
                aboutService.startAboutClient(bus);
            } catch (Exception e) {
                logger.error(TAG, "Unable to start AboutService, Error: " + e.getMessage());
            }

            /* Initialize NotificationService */
            notificationService = NotificationService.getInstance();
            try {
                notificationService.initReceive(bus, this);
            } catch (NotificationServiceException nse) {
                logger.error(TAG, "Unable to start NotificationService, Error: " + nse.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error occurred, failed to start the NotificationReceiver, Error: '" + e.getMessage() + "'");
                Toast.makeText(NotificationViewer.this, "Failed to start the NotificationReceiver", Toast.LENGTH_LONG).show();
            }

            // DO the AJ addMatch.
            Status s = bus.addMatch(SESSIONLESS_MATCH_RULE);
            logger.info(TAG, "BusAttachment.addMatch() status = " + s);
            Toast.makeText(getApplicationContext(), getString(R.string.toast_init_success), Toast.LENGTH_LONG).show();

        }

        // ------------------------ Disconnect --------------------------------

        /**
         * Release all resources acquired in connect.
         */
        private void disconnect() {
            if (notificationService != null) {
                try {
                    notificationService.shutdownReceiver();
                } catch (NotificationServiceException nse) {
                    logger.error(TAG, "NotificationService failed to stop receiver, Error: " + nse.getMessage());
                }
            }
            if (aboutService != null) {
                try {
                    aboutService.stopAboutClient();
                } catch (Exception e) {
                    logger.error(TAG, "AboutService failed to stop client, Error: " + e.getMessage());
                }
            }
        }

        private void shutdown() {
            try {
                notificationService.shutdown();
            } catch (Exception e) {
                logger.error(TAG, "Shutdown failed to stop receiver, Error: " + e.getMessage());
            }
        }

        @Override
        public void receive(Notification notification) {
            logger.debug(
                    TAG,
                    String.format(
                            "Received new Notification, Id: '%s', MessageType: '%s' DeviceId: '%s', DeviceName: '%s', CustomAvPairs: '%s', FirstMsgLang: '%s', FirstMsg: '%s'",
                            notification.getMessageId(), notification.getMessageType(), notification.getDeviceId(), notification.getDeviceName(), notification
                                    .getCustomAttributes().toString(), notification.getText().get(0).getLanguage(), notification.getText().get(0).getText()));

            sendMessage(obtainMessage(NEW_NOTIFICATION, notification));
        }

        @Override
        public void dismiss(int notifId, UUID appId) {
            Log.d(TAG, " !!!!! DISMISS RECEIVED !!!! : '" + notifId + "', appId: '" + appId + "'");
        }
    }

    private int m_notifId = 0;

    private void showNotification(Notification notification) {
        android.app.Notification taskbarNotification = new android.app.Notification.Builder(this).setContentTitle(notification.getDeviceName())
                .setContentText(getLocalizedText(notification.getText())).setSmallIcon(R.drawable.ajnv_notify_icon)
                // Dummy intent, because we have no Activity to launch
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(), 0))
                // not opening anything. So a click on the notification should dismiss it, otherwise who would
                .setAutoCancel(true).getNotification(); // build() requires min API 16

        m_androidNotificationManager.notify(m_notifId++, taskbarNotification);
    }

    private void showNotificationNew(final Notification notification) {
        NotificationMessageType messageType = notification.getMessageType();
        switch (messageType) {
        case EMERGENCY:
            if (shouldShowNotificationWithImage(notification.getRichIconUrl())) {
                showNotificationWithImage(notification);
            } else {
                showNotificationWithoutImage(notification);
            }
            break;

        default:
            showNotificationWithoutImage(notification);
            break;
        }
    }

    private boolean shouldShowNotificationWithImage(String imageUrl) {
        boolean retval = false;
        if (null != imageUrl && (imageUrl.startsWith(HTTP_PREFIX) || imageUrl.startsWith(FTP_PREFIX))) {
            retval = true;
        }
        return retval;
    }

    private void showNotificationWithImage(final Notification notification) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String deviceName = notification.getDeviceName();
                CharSequence notificationText = getLocalizedText(notification.getText());
                String richIconUrl = notification.getRichIconUrl();

                Log.i(TAG, "showNotificationWithImage - url: " + richIconUrl);

                TextView deviceNameTextView = (TextView) emergencyNotificationsLayout.findViewById(R.id.emergency_notification_title_bar_device_name);
                deviceNameTextView.setText(deviceName);

                ImageView notificationIconImageView = (ImageView) emergencyNotificationsLayout.findViewById(R.id.emergency_notification_title_bar_about_image);

                // repurpose the richIconUrl for use with the notification image view
                if (shouldShowNotificationWithImage(richIconUrl)) {
                    TextView emergencyNotificationMessageTextView = (TextView) emergencyNotificationsLayout.findViewById(R.id.emergency_notification_message);
                    emergencyNotificationMessageTextView.setText(notificationText);

                    ImageView emergencyNotificationImageView = (ImageView) emergencyNotificationsLayout.findViewById(R.id.emergency_notification_image);
                    new SetImageBitmapTask(emergencyNotificationImageView, richIconUrl).execute();
                }

                UIUtil.setDeviceIcon(notificationIconImageView, notification.getAppId(), true, IGNORE_VIEW_ID);
            }
        });

    }

    private void showNotificationWithoutImage(final Notification notification) {
        final int viewId = generateViewId();

        handler.post(new Runnable() {
            @Override
            public void run() {
                String deviceName = notification.getDeviceName();
                CharSequence notificationText = getLocalizedText(notification.getText());

                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                final View layout = inflater.inflate(R.layout.notification_toast_layout, null);

                // set the device name
                TextView deviceNameTextView = (TextView) layout.findViewById(R.id.notification_toast_device_name);
                deviceNameTextView.setText(deviceName);

                // set the text
                TextView messageTextView = (TextView) layout.findViewById(R.id.notification_toast_message);
                messageTextView.setText(notificationText);

                String notificationTextString = "";
                if (null != notificationText) {
                    notificationTextString = notificationText.toString();
                }
                logger.debug(TAG, "notificationTextString: " + notificationTextString);

                // === precalc the number of lines ===
                // need to do this preprocessing, so that text view expands to 4 lines

                // find the space that the toast's layout allows the text view
                layout.measure(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

                int maxWidth = messageTextView.getMeasuredWidth();
                logger.debug(TAG, "maxWidth=" + maxWidth);

                // simulate a layout, so that we can predict the final TextView height
                TextPaint textPaint = messageTextView.getPaint();
                StaticLayout staticLayout = new StaticLayout(notificationTextString, textPaint, maxWidth, Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                int minHeight = staticLayout.getHeight();
                int maxHeight = (int) (MAX_LINES * (textPaint.descent() - textPaint.ascent()));
                logger.debug(TAG, "minHeight=" + minHeight);

                // assign a minimum height to the TextView, according to simulation result
                messageTextView.setMinHeight(Math.min(maxHeight, minHeight)); // a too high minimum, will pass the 4 lines limit.
                messageTextView.setMaxLines(MAX_LINES);

                // === end precalc the number of lines ===

                ImageView notificationIconImageView = (ImageView) layout.findViewById(R.id.notification_toast_image);
                layout.setId(viewId);

                nonEmergencyNotificationsLayout.addView(layout, nonEmergencyNotificationsLayout.getChildCount());
                UIUtil.setDeviceIcon(notificationIconImageView, notification.getAppId(), false, viewId);
            }
        });
        new DisplayNotificationTask().execute((long) viewId, System.currentTimeMillis());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        }, POST_DELAYED_IN_MS_BEFORE_SCROLL);
    }

    private class MakeEmergencyNotificationInvisibleTask extends AsyncTask<Long, Void, Void> {

        @Override
        protected Void doInBackground(Long... params) {
            if (null != params && params.length > 0) {
                long startTime = params[0];
                try {
                    Thread.sleep(getSleepTime(startTime, PropertiesManagerFactory.getPropertiesManager().getNotificationFullscreenAlertTimeoutInMs()));
                } catch (InterruptedException e) {
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    emergencyNotificationsLayout.setVisibility(View.INVISIBLE);
                }
            });
        }

    }

    private class DisplayNotificationTask extends AsyncTask<Long, Void, Integer> {
        private static final int INVALID_VIEW_ID = -1;

        @Override
        protected Integer doInBackground(Long... params) {
            int viewId = INVALID_VIEW_ID;
            if (null != params && params.length > 1) {
                viewId = params[0].intValue();
                long startTime = params[1];
                try {
                    Thread.sleep(getSleepTime(startTime, PropertiesManagerFactory.getPropertiesManager().getNotificationPopupTimeoutInMs()));
                } catch (InterruptedException e) {
                }
            }
            return viewId;
        }

        @Override
        protected void onPostExecute(Integer viewId) {
            logger.debug(TAG, "onPostExecute()");
            if (INVALID_VIEW_ID != viewId) {
                final View view = nonEmergencyNotificationsLayout.findViewById(viewId);
                if (null != view) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            nonEmergencyNotificationsLayout.removeView(view);
                        }
                    });
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            scrollView.fullScroll(View.FOCUS_DOWN);
                        }
                    }, POST_DELAYED_IN_MS_BEFORE_SCROLL);
                }
            }
        }

    }

    private static long getSleepTime(long startTime, long sleepTimeInMs) {
        long retval = 0;
        long curTime = System.currentTimeMillis();
        long diff = curTime - startTime;
        if (diff < sleepTimeInMs) {
            retval = sleepTimeInMs - diff;
        }
        return retval;
    }

    private class SetImageBitmapTask extends AsyncTask<Void, Void, Void> {
        private final ImageView imageView;
        private final String url;
        private Bitmap bitmap;

        SetImageBitmapTask(ImageView imageView, String url) {
            this.imageView = imageView;
            this.url = url;
        }

        @Override
        protected Void doInBackground(Void... params) {
            bitmap = getImageBitmap(url);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (null != bitmap) {
                imageView.setImageBitmap(bitmap);
            }

            emergencyNotificationsLayout.setVisibility(View.VISIBLE);
            new MakeEmergencyNotificationInvisibleTask().execute(System.currentTimeMillis());
        }

        private Bitmap getImageBitmap(String url) {
            Bitmap bitmap = null;
            try {
                URL aURL = new URL(url);
                URLConnection conn = aURL.openConnection();
                conn.connect();
                InputStream is = conn.getInputStream();
                BufferedInputStream bis = new BufferedInputStream(is);
                bitmap = BitmapFactory.decodeStream(bis);
                bis.close();
                is.close();
            } catch (IOException e) {
                Log.e(TAG, "Error getting bitmap", e);
                e.printStackTrace();
            }
            return bitmap;
        }
    }

    /**
     * Show the Android toast message
     * 
     * @param msg
     */
    // need to finalize position, coloring and size
    public void showToast(Notification notification) {
        String deviceName = notification.getDeviceName();
        CharSequence notificationText = getLocalizedText(notification.getText());
        int iconResource = getIcon(notification);

        doShowToast(iconResource, deviceName, notificationText);
    }

    /**
     * @param iconResource
     * @param notification
     * @param deviceName
     * @param notificationText
     */
    public void doShowToast(int iconResource, String deviceName, CharSequence notificationText) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.notification_toast_layout, null);

        final Toast toast = new Toast(getApplicationContext());

        // set the device name
        TextView deviceNameTextView = (TextView) layout.findViewById(R.id.notification_toast_device_name);
        deviceNameTextView.setText(deviceName);

        // set the text
        TextView messageTextView = (TextView) layout.findViewById(R.id.notification_toast_message);
        messageTextView.setText(notificationText);

        String notifText = notificationText.toString();
        logger.debug(TAG, notifText);

        // === precalc the number of lines ===
        // need to do this preprocessing, so that text view expands to 4 lines

        // find the space that the toast's layout allows the text view
        layout.measure(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        int maxWidth = messageTextView.getMeasuredWidth();
        logger.debug(TAG, "maxWidth=" + maxWidth);

        // simulate a layout, so that we can predict the final TextView height
        TextPaint textPaint = messageTextView.getPaint();
        StaticLayout staticLayout = new StaticLayout(notifText, textPaint, maxWidth, Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        int minHeight = staticLayout.getHeight();
        int maxHeight = (int) (MAX_LINES * (textPaint.descent() - textPaint.ascent()));
        logger.debug(TAG, "minHeight=" + minHeight);

        // assign a minimum height to the TextView, according to simulation result
        messageTextView.setMinHeight(Math.min(maxHeight, minHeight)); // a too high minimum, will pass the 4 lines limit.
        messageTextView.setMaxLines(MAX_LINES);

        // === end precalc the number of lines ===

        // set the correct icon
        ImageView notificationIcon = (ImageView) layout.findViewById(R.id.notification_toast_image);
        if (iconResource > -1) {
            notificationIcon.setImageResource(iconResource);
        }

        toast.setGravity(Gravity.BOTTOM, 0, 70);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);

        toast.show();
    }

    private int getIcon(Notification notification) {
        switch (notification.getMessageType()) {
        case EMERGENCY:
            return R.drawable.tv_notify_urgent;
        case WARNING:
            return R.drawable.tv_notify_warning;
        case INFO:
            return R.drawable.tv_notify_regular;
        default:
            return R.drawable.tv_notify_regular;
        }
    }

    private CharSequence getLocalizedText(List<NotificationText> notificationTextList) {
        for (NotificationText nt : notificationTextList) {
            if (nt.getLanguage().equalsIgnoreCase(m_languageTag)) {
                return nt.getText();
            }
        }
        if (!notificationTextList.isEmpty()) {
            return notificationTextList.get(0).getText();
        }
        return "";
    }

    /**
     * Performs all the preparation before starting the service
     */
    private void prepareAJ() throws NotificationServiceException {
        bus = new BusAttachment("TVNotificationService", BusAttachment.RemoteMessage.Receive);

        Status conStatus = bus.connect();
        if (conStatus != Status.OK) {
            logger.error(TAG, "Failed connect to bus, Error: '" + conStatus + "'");
            throw new NotificationServiceException("Failed connect to bus, Error: '" + conStatus + "'");
        }

        /* Make all AllJoyn calls through a separate handler thread to prevent blocking the UI. */
        HandlerThread busThread = new HandlerThread("BusHandler");
        busThread.start();
        busHandler = new BusHandler(bus, busThread.getLooper(), this, getResources().getString(R.string.app_name));

        busHandler.sendEmptyMessage(BusHandler.LAUNCH_SERVER_MODE);
    }// prepareAJ

    private static final AtomicInteger nextGeneratedViewId = new AtomicInteger(1);

    private static int generateViewId() {
        while (true) {
            final int retval = nextGeneratedViewId.get();
            int nextValue = retval + 1;
            // Id numbers larger than 0x00FFFFFF are reserved, so roll over, but not to 0.
            if (nextValue > 0x00FFFFFF)
                nextValue = 1;
            if (nextGeneratedViewId.compareAndSet(retval, nextValue)) {
                return retval;
            }
        }
    }

}
