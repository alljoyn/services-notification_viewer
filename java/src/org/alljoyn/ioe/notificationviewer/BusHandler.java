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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.alljoyn.about.AboutKeys;
import org.alljoyn.about.AboutServiceImpl;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.Variant;
import org.alljoyn.ioe.notificationviewer.logic.DeviceManagerImpl;
import org.alljoyn.services.common.AnnouncementHandler;
import org.alljoyn.services.common.BusObjectDescription;
import org.alljoyn.services.common.PropertyStore;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class BusHandler extends Handler {
    private final Context mContext;

    /*
     * Name used as the prefix for the well-known name that is advertised. This name must be a unique name both to the bus and to the network as a whole.
     */
    private static final short CONTACT_PORT = 25;

    private BusAttachment mBus;

    // Need a property store for About
    private PropertyStore propertyStore;

    public String deviceName;

    private SharedPreferences sharedPrefs;

    /* These are the messages sent to the BusHandler from the UI. */
    public static final int LAUNCH_SERVER_MODE = 1;
    public static final int SHUTDOWN = 10;

    public BusHandler(BusAttachment bus, Looper looper, Context context, String displayName) {
        super(looper);
        initialize(bus);
        this.mContext = context;
        this.sharedPrefs = mContext.getSharedPreferences(Constants.SHARED_PREFS_FILENAME, Context.MODE_PRIVATE);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case LAUNCH_SERVER_MODE: {
            launchInServerMode();
            break;
        }
        /* Release all resources acquired in connect. */
        case SHUTDOWN: {
            shutdown();
            break;
        }
        default:
            break;
        }
    }

    private void initialize(BusAttachment bus) {
        mBus = bus;
    }

    private void launchInServerMode() {
        Log.d(Constants.TAG, "Launching in ServerMode");
        setupPropertyStore();
        try {
            // add ourselves to About
            AboutServiceImpl.getInstance().startAboutServer(CONTACT_PORT, this.propertyStore, this.mBus);
            String mimeType = "image/png";
            String iconUrl = "http://ioe-icons.xrunq.qualcomm.com/1/device_tv_icon.png";
            AboutServiceImpl.getInstance().registerIcon(mimeType, iconUrl, null);
            List<BusObjectDescription> busObjectDescriptionList = new ArrayList<BusObjectDescription>();
            AboutServiceImpl.getInstance().addObjectDescriptions(busObjectDescriptionList);

            AboutServiceImpl.getInstance().addAnnouncementHandler(new AnnouncementHandler() {
                @Override
                public void onAnnouncement(final String serviceName, final short port, final BusObjectDescription[] objectDescriptions,
                        final Map<String, Variant> serviceMetadata) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).onAnnouncement(serviceName, port, objectDescriptions, serviceMetadata);
                        }
                    }).start();

                }

                @Override
                public void onDeviceLost(final String serviceName) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).onDeviceLost(serviceName, false);
                        }
                    }).start();
                }
            }, null);

            // create session to allow others to get About icon info
            createSession();

            // Now that we are setup, announce via About
            AboutServiceImpl.getInstance().announce();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void createSession() {
        Mutable.ShortValue contactPort = new Mutable.ShortValue(CONTACT_PORT);

        SessionOpts sessionOpts = new SessionOpts();
        sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
        sessionOpts.isMultipoint = true;
        sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;
        sessionOpts.transports = SessionOpts.TRANSPORT_ANY;

        Status status = mBus.bindSessionPort(contactPort, sessionOpts, new NotificationViewerSessionPortListener());
        Log.d(Constants.TAG, String.format("BusAttachment.bindSessionPort(%d, %s)", contactPort.value, sessionOpts.toString()));
    }

    private void closeSession() {
        String wellknownName = mBus.getUniqueName();

        mBus.cancelAdvertiseName(wellknownName, SessionOpts.TRANSPORT_ANY);
        mBus.unbindSessionPort(CONTACT_PORT);
        Log.d(Constants.TAG, "Removed ability for others to join");
    }

    private void shutdown() {
        try {
            AboutServiceImpl.getInstance().stopAboutServer();
            AboutServiceImpl.getInstance().unregisterIcon();
            closeSession();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mBus.disconnect();
        getLooper().quit();
    }

    private class NotificationViewerSessionPortListener extends SessionPortListener {
        @Override
        public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
            if (CONTACT_PORT == sessionPort) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void sessionJoined(short sessionPort, int id, String joiner) {
        }
    }

    private String generateDeviceId() {
        String retval;
        if (null != android.os.Build.SERIAL && false == android.os.Build.SERIAL.isEmpty()) {
            String tempId = "1231232145667745675477";
            retval = android.os.Build.SERIAL + tempId.substring(android.os.Build.SERIAL.length());
        } else {
            retval = "12312321456677" + String.format("%08d", new Random().nextInt(45675477));
        }
        return retval;
    }

    private String generateAppId() {
        final char DEFAULT_CHAR_TO_ADD_IF_INVALID_CHAR = '0';
        String buildSerialString = android.os.Build.SERIAL.toLowerCase();
        char[] alphanumericArray = { 'a', 'b', 'c', 'd', 'e', 'f', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' };
        Set<Character> alphanumericSet = new HashSet<Character>();
        for (char c : alphanumericArray) {
            alphanumericSet.add(c);
        }
        StringBuffer buffer = new StringBuffer();
        for (char c : buildSerialString.toCharArray()) {
            if (alphanumericSet.contains(c)) {
                buffer.append(c);
            } else {
                buffer.append(DEFAULT_CHAR_TO_ADD_IF_INVALID_CHAR);
            }
        }
        buildSerialString = buffer.toString();

        if (null != buildSerialString && false == buildSerialString.isEmpty()) {
            String tempId = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
            if (buildSerialString.length() <= 12) {
                String retvalCandidate = tempId.substring(0, tempId.length() - buildSerialString.length()) + buildSerialString;
                try {
                    // see if it's a valid UUID
                    UUID.fromString(retvalCandidate);
                    return retvalCandidate;
                } catch (Exception e) {
                    Log.w(Constants.TAG, "Using random app id, as unable to build valid app id with potentially modified build serial: "
                            + android.os.Build.SERIAL);
                    return String.format("%08d", new Random().nextInt(38400000)) + "-8cf0-11bd-b23e-10b96e4ef00d";
                }
            } else {
                String retvalCandidate = tempId.substring(0, tempId.length() - 12) + buildSerialString.substring(0, 12);
                try {
                    // see if it's a valid UUID
                    UUID.fromString(retvalCandidate);
                    return retvalCandidate;
                } catch (Exception e) {
                    Log.w(Constants.TAG, "Using random app id, as unable to build valid app id with potentially modified build serial: "
                            + android.os.Build.SERIAL);
                    return String.format("%08d", new Random().nextInt(38400000)) + "-8cf0-11bd-b23e-10b96e4ef00d";
                }
            }
        } else {
            return String.format("%08d", new Random().nextInt(38400000)) + "-8cf0-11bd-b23e-10b96e4ef00d";
        }
    }

    private String getDeviceName() {
        String deviceName = sharedPrefs.getString(Constants.SHARED_PREFS_KEY_DEVICE_NAME, null);
        if (null == deviceName || deviceName.isEmpty()) {
            deviceName = generateDeviceName();
        }
        return deviceName;
    }

    private String generateDeviceName() {
        if (null != android.os.Build.SERIAL && false == android.os.Build.SERIAL.isEmpty()) {
            return "MyDevice" + android.os.Build.SERIAL;
        } else {
            return "MyDevice" + new Random().nextInt(9999);
        }
    }

    private void setupPropertyStore() {
        Map<String, List<PropertyStoreImpl.Property>> data = new HashMap<String, List<PropertyStoreImpl.Property>>();

        data.put(
                AboutKeys.ABOUT_DEFAULT_LANGUAGE,
                new ArrayList<PropertyStoreImpl.Property>(Arrays
                        .asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_DEFAULT_LANGUAGE, "en", true, true, true))));

        data.put(
                AboutKeys.ABOUT_DEVICE_NAME,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_DEVICE_NAME, getDeviceName(), true,
                        true, true))));

        data.put(
                AboutKeys.ABOUT_DEVICE_ID,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_DEVICE_ID, generateDeviceId(), true,
                        false, true))));

        data.put(
                AboutKeys.ABOUT_DESCRIPTION,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_DESCRIPTION, "This is a TV", true,
                        false, false))));

        final UUID uid = UUID.fromString(generateAppId());
        data.put(AboutKeys.ABOUT_APP_ID,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_APP_ID, uid, true, false, true))));

        data.put(
                AboutKeys.ABOUT_APP_NAME,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_APP_NAME, mContext.getResources()
                        .getString(R.string.app_name), true, false, true))));

        data.put(
                AboutKeys.ABOUT_MANUFACTURER,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_MANUFACTURER, "Company", true, false,
                        true))));

        data.put(
                AboutKeys.ABOUT_MODEL_NUMBER,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_MODEL_NUMBER, "SampleModelNumber", true,
                        false, true))));

        data.put(
                AboutKeys.ABOUT_SUPPORTED_LANGUAGES,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_SUPPORTED_LANGUAGES,
                        new HashSet<String>() {
                            {
                                add("en");
                                add("sp");
                                add("ru");
                            }
                        }, true, false, true))));

        data.put(
                AboutKeys.ABOUT_DATE_OF_MANUFACTURE,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_DATE_OF_MANUFACTURE, "10/1/2199", true,
                        false, true))));

        data.put(
                AboutKeys.ABOUT_SOFTWARE_VERSION,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_SOFTWARE_VERSION,
                        "12.20.44 build 44454", true, false, true))));

        // should update this field to the proper version (ex. "14.02" or "14.06")
        data.put(
                AboutKeys.ABOUT_AJ_SOFTWARE_VERSION,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_AJ_SOFTWARE_VERSION, "14.xx", true,
                        false, true))));

        data.put(
                AboutKeys.ABOUT_HARDWARE_VERSION,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_HARDWARE_VERSION, "355.499. b", true,
                        false, true))));

        data.put(
                AboutKeys.ABOUT_SUPPORT_URL,
                new ArrayList<PropertyStoreImpl.Property>(Arrays.asList(new PropertyStoreImpl.Property(AboutKeys.ABOUT_SUPPORT_URL, "http://www.allseenalliance.org",
                        true, false, true))));

        this.propertyStore = new PropertyStoreImpl(data, sharedPrefs);

    }

}
