package org.alljoyn.ioe.notificationviewer;
/******************************************************************************
* Copyright (c) 2013, AllSeen Alliance. All rights reserved.
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

import java.util.List;

import org.alljoyn.about.AboutService;
import org.alljoyn.about.AboutServiceImpl;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.PasswordManager;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.alljoyn.DaemonInit;
import org.alljoyn.ns.Notification;
import org.alljoyn.ns.NotificationReceiver;
import org.alljoyn.ns.NotificationService;
import org.alljoyn.ns.NotificationServiceException;
import org.alljoyn.ns.NotificationText;
import org.alljoyn.services.common.utils.GenericLogger;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class NotificationViewer extends Service
{
	/**
	 * Max lines in toast
	 */
	private static final int MAX_LINES = 4;

	private final static String TAG = "NotificationViewer";

	/**
	 * Reference to AboutClient
	 */
	private AboutService aboutClient;

	/**
	 * Reference to NotificationService
	 */
	private NotificationService notificationService;

    private NotificationManager m_androidNotificationManager;
    
	private AsyncHandler m_asyncHandler;
	
    private String m_languageTag = "en";
    
	/* Load the native alljoyn_java library. */
	static {
		System.loadLibrary("alljoyn_java");
	}

    /** 
     * The daemon should advertise itself "quietly" (directly to the calling port)
     * This is to reply directly to a TC looking for a daemon 
     */  
    private static final String DAEMON_NAME                 = "org.alljoyn.BusNode.IoeService";
    
    /** 
     * The daemon should advertise itself "quietly" (directly to the calling port)
     * This is to reply directly to a TC looking for a daemon 
     */  
    private static final String DAEMON_QUIET_PREFIX         = "quiet@";

    /** 
     * The daemon authentication method
     */  
    private static final String DAEMON_AUTH                 = "ALLJOYN_PIN_KEYX";

    private static final String DAEMON_PWD                  = "000000";

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
            //To change body of implemented methods use File | Settings | File Templates.
            Log.i(TAG, msg);
        }

        @Override
        public void warn(String TAG, String msg) {
            //To change body of implemented methods use File | Settings | File Templates.
            Log.w(TAG, msg);
        }

        @Override
        public void error(String TAG, String msg) {
            //To change body of implemented methods use File | Settings | File Templates.
            Log.e(TAG, msg);
        }

        @Override
        public void fatal(String TAG, String msg) {
            //To change body of implemented methods use File | Settings | File Templates.
            Log.wtf(TAG, msg);
        }
    };

	public NotificationViewer()
	{
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		// TODO: Return the communication channel to the service.
		throw new UnsupportedOperationException("Not yet implemented");
	}
	
	@Override
	public void onCreate() {
		super.onCreate();

	    m_androidNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

	    HandlerThread busThread = new HandlerThread("BusHandler");
		busThread.start();
		m_asyncHandler = new AsyncHandler(busThread.getLooper());
		m_asyncHandler.sendEmptyMessage(AsyncHandler.CONNECT);
		//m_asyncHandler.sendEmptyMessage(AsyncHandler.SIMULATE);
	}

    @Override    
    public int onStartCommand(Intent intent, int flags, int startId) 
    {        
    	// Continue running until explicitly being stopped.        
    	return START_STICKY;    
    }

	@Override
	public void onDestroy() 
	{
		/* Disconnect to prevent any resource leaks. */
		m_asyncHandler.shutdown();
		m_asyncHandler.getLooper().quit();

		super.onDestroy();

	}

	/* This class will handle all AllJoyn calls. See onCreate(). */
	class AsyncHandler extends Handler implements NotificationReceiver 
	{

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
				
				Notification notification = (Notification)msg.obj;
				showNotification(notification);
				showToast(notification);
				break;
			}
			case SIMULATE: {
				String deviceName = "Simulator";
				String abcde = "abcdefghijklmn";
				StringBuffer sb = new StringBuffer();
				for (int i=0; i < 15; i++)
				{
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
		private void connect() 
		{
			try {
				logger.info(TAG, "Initializing AllJoyn");
				prepareAJ();
			} catch (NotificationServiceException nse) {
				 logger.error(TAG, "Failed to initialize AllJoyn, Error: '" + nse.getMessage() + "'");
	             return;
			}

			/* Initialize AboutService */
			aboutClient = AboutServiceImpl.getInstance();
			aboutClient.setLogger(logger);
			try {
				aboutClient.startAboutClient(bus);
			} catch (Exception e) {
				logger.error(TAG, "Unable to start AboutService, Error: " + e.getMessage());
			}
			
			/* Initialize NotificationService */
			notificationService = NotificationService.getInstance();
			try {
				notificationService.initReceive(bus, this);
			} catch (NotificationServiceException nse) {
				logger.error(TAG, "Unable to start NotificationService, Error: " + nse.getMessage());
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
				} catch(NotificationServiceException nse) {
					logger.error(TAG, "NotificationService failed to stop receiver, Error: " + nse.getMessage());
				}
			}
			if (aboutClient != null) {
        		try {
					aboutClient.stopAboutClient();
				} catch (Exception e) {
					logger.error(TAG, "AboutService failed to stop client, Error: " + e.getMessage());
				}
			}
		 }

		private void shutdown()
		{
			try
			{
				notificationService.shutdown();
			} catch (Exception e)
			{
				logger.error(TAG, "Shutdown failed to stop receiver, Error: " + e.getMessage());
			}
		}

		@Override
		public void receive(Notification notification)
		{
			logger.debug(TAG, String.format("Received new Notification, Id: '%s', MessageType: '%s' DeviceId: '%s', DeviceName: '%s', CustomAvPairs: '%s', FirstMsgLang: '%s', FirstMsg: '%s'", notification.getMessageId(), notification.getMessageType(), notification.getDeviceId(), notification.getDeviceName(), notification.getCustomAttributes().toString(), notification.getText().get(0).getLanguage(), notification.getText().get(0).getText()));

			sendMessage(obtainMessage(NEW_NOTIFICATION,notification));
		}
     }
	
	private int m_notifId = 0; 
	private void showNotification(Notification notification) 
	{      
		android.app.Notification taskbarNotification = new android.app.Notification.Builder(this)
			.setContentTitle(notification.getDeviceName())
			.setContentText(getLocalizedText(notification.getText()))
			.setSmallIcon(R.drawable.ajnv_notify_icon)
			// Dummy intent, because we have no Activity to launch
			.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(), 0))
			// not opening anything. So a click on the notification should dismiss it, otherwise who would
			.setAutoCancel(true).getNotification(); //build() requires min API 16
		
		m_androidNotificationManager.notify(m_notifId++, taskbarNotification);
	}
	
	/**
	 * Show the Android toast message
	 * @param msg
	 */
    //need to finalize position, coloring and size
	public void showToast(Notification notification)
	{
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
	public void doShowToast(int iconResource, String deviceName, CharSequence notificationText)
	{
		LayoutInflater inflater = (LayoutInflater) getSystemService( Context.LAYOUT_INFLATER_SERVICE );
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
		logger.debug(TAG, "maxWidth="+maxWidth);

		// simulate a layout, so that we can predict the final TextView height
		TextPaint textPaint = messageTextView.getPaint();
		StaticLayout staticLayout = new StaticLayout(notifText, textPaint, maxWidth , Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
		int minHeight = staticLayout.getHeight();
		int maxHeight = (int) (MAX_LINES * (textPaint.descent() - textPaint.ascent())); 
		logger.debug(TAG, "minHeight="+minHeight);

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
	
	private int getIcon(Notification notification)
	{
		switch (notification.getMessageType()) 
		{
		case EMERGENCY: return R.drawable.mpq_tv_notify_urgent;  
		case WARNING: return R.drawable.mpq_tv_notify_warning;  
		case INFO: return R.drawable.mpq_tv_notify_regular;
		default: return R.drawable.mpq_tv_notify_regular;
		}
	}

	private CharSequence getLocalizedText(List<NotificationText> notificationTextList)
	{
		for ( NotificationText nt : notificationTextList ) 
		{
			if (nt.getLanguage().equalsIgnoreCase(m_languageTag))
			{
				return nt.getText();
			}
		}
		if (!notificationTextList.isEmpty())
		{
			return notificationTextList.get(0).getText();
		}
		return "";
	}

    /**
     * Performs all the preparation before starting the service
     */
    private void prepareAJ() throws NotificationServiceException {
        //Create my own BusAttachment
        DaemonInit.PrepareDaemon(this);

        logger.debug(TAG, "Create the BusAttachment");
        bus = new BusAttachment("NotificationService", BusAttachment.RemoteMessage.Receive);

        //For verbose AJ logging use the following lines
        //busAttachment.setDaemonDebug("ALL", 7);
        //busAttachment.setLogLevels("ALLJOYN=7");
        //busAttachment.setLogLevels("ALL=7");
        //busAttachment.useOSLogging(true);

        //setting the password for the daemon to allow thin clients to connect
        logger.debug(TAG, "Setting daemon password");
        Status pasStatus = PasswordManager.setCredentials(DAEMON_AUTH, DAEMON_PWD);

        if ( pasStatus != Status.OK ) {
            logger.error(TAG, "Failed to set password for daemon, Error: " + pasStatus);
        }

        Status conStatus = bus.connect();
        if ( conStatus != Status.OK ) {
            logger.error(TAG, "Failed connect to bus, Error: '" + conStatus + "'");
            throw new NotificationServiceException("Failed connect to bus, Error: '" + conStatus + "'");
        }

        //Advertise the daemon so that the thin client can find it
        advertiseDaemon();
    }//prepareAJ

    /**
     * Advertise the daemon so that the thin client can find it
     * @param logger
     */
   private void advertiseDaemon() throws NotificationServiceException {
       //request the name   
       int flag = BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE;
       Status reqStatus = bus.requestName(DAEMON_NAME, flag);
        if (reqStatus == Status.OK) {
            //advertise the name with a quite prefix for TC to find it
            Status adStatus = bus.advertiseName(DAEMON_QUIET_PREFIX + DAEMON_NAME, SessionOpts.TRANSPORT_ANY);
            if (adStatus != Status.OK){
                bus.releaseName(DAEMON_NAME); 
                logger.error(TAG, "Failed to advertise daemon name " + DAEMON_NAME + ", Error: '" + adStatus + "'"); 
                throw new NotificationServiceException("Failed to advertise daemon name '" + DAEMON_NAME + "', Error: '" + adStatus + "'"); 
            } 
            else{ 
                logger.debug(TAG, "Succefully advertised daemon name " + DAEMON_NAME); 
            }
        }
    }//advertiseDaemon

}
