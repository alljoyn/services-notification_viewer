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

package org.alljoyn.ioe.notificationviewer.logic;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.alljoyn.about.AboutKeys;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.Variant;
import org.alljoyn.ioe.notificationviewer.BackgroundService;
import org.alljoyn.ioe.notificationviewer.Constants;
import org.alljoyn.ioe.notificationviewer.NotificationViewer;
import org.alljoyn.ioe.notificationviewer.R;
import org.alljoyn.ioe.notificationviewer.logic.Interface.Device;
import org.alljoyn.ioe.notificationviewer.logic.Interface.Device.DeviceStatus;
import org.alljoyn.ioe.notificationviewer.logic.Interface.DeviceManager;
import org.alljoyn.ioe.notificationviewer.logic.Interface.DeviceResponse;
import org.alljoyn.ioe.notificationviewer.logic.Interface.DeviceResponse.ResponseCode;
import org.alljoyn.ioe.notificationviewer.logic.Interface.IntentActions;
import org.alljoyn.ioe.notificationviewer.logic.Interface.IntentExtraKeys;
import org.alljoyn.services.common.BusObjectDescription;
import org.alljoyn.services.common.utils.TransportUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

public class DeviceManagerImpl implements DeviceManager {

    private final String TAG = "DeviceManagerImpl";

    Map<String, Device> m_deviceList;
    protected static Context m_context;

    private static DeviceManager m_current;

    private ImageCacheManager m_ImageCacheManager = null;
    private boolean m_isRunning = true;
    private static Bitmap defaultImage;

    public static DeviceManager getInstance() {

        if (m_current == null)
            m_current = new DeviceManagerImpl();
        return m_current;
    }

    // protected for the DummyDeviceManager
    protected DeviceManagerImpl() {
    }

    @Override
    public void init(Context c, String keyStorekeyStoreFileName) {
        m_context = c;
        if (m_deviceList == null)
            m_deviceList = new HashMap<String, Device>();

        SharedPreferencesManager.init(c);

        m_ImageCacheManager = new ImageCacheManager();
        m_ImageCacheManager.init();

        defaultImage = BitmapFactory.decodeResource(c.getResources(), R.drawable.my_devices_icon_reg);
        loadDevicesFromPreference();
    }

    public static Bitmap getDefaultBimapImage() {
        return defaultImage;
    }

    @Override
    public ArrayList<Device> getDevices() {
        if (m_deviceList != null) {
            return new ArrayList<Device>(m_deviceList.values());
        } else {
            return new ArrayList<Device>();
        }

    }

    @Override
    public boolean contains(UUID deviceID) {
        return m_deviceList.containsKey(deviceID.toString());
    }

    @Override
    public Device getDevice(UUID deviceID) {
        if (m_deviceList == null || deviceID == null)
            return null;

        return m_deviceList.get(deviceID.toString());
    }

    @Override
    public Device removeDevice(UUID deviceID) {
        if (deviceID == null)
            return null;
        if (m_ImageCacheManager != null) {
            m_ImageCacheManager.removeDeviceFromUUUIDList(deviceID);
        }
        return m_deviceList.remove(deviceID.toString());
    }

    protected Device getDeviceByServiceName(String serviceName) {
        Collection<Device> devices = getDevices();
        if (devices == null)
            return null;

        Iterator<Device> iterator = devices.iterator();
        if (iterator != null) {
            Device current;
            while (iterator.hasNext()) {
                current = iterator.next();
                if (((DeviceImpl) current).getServiceName() != null && ((DeviceImpl) current).getServiceName().equals(serviceName)) {
                    return current;
                }
            }
        }

        return null;
    }

    private void loadDevicesFromPreference() {

        Log.i(TAG, "loadDevicesFromPreference");
        Map<String, String> devices = SharedPreferencesManager.getAllDevices();
        if (devices == null || devices.isEmpty())
            return;
        Iterator<Entry<String, String>> iterator = devices.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry pairs = iterator.next();
            String deviceIdTmp = (String) pairs.getKey();
            UUID deviceId = UUID.fromString(deviceIdTmp);

            Log.i(TAG, "loadDevicesFromPreference load from " + deviceId);
            boolean newDevice;
            DeviceImpl d = (DeviceImpl) getDevice(deviceId);
            if (d != null)// we have this device, probably from announcement
            {
                newDevice = false;
            } else {
                d = new DeviceImpl("", deviceId);
                newDevice = true;
            }
            String deviceData = (String) pairs.getValue();
            d.setId(deviceId);
            String[] fields = deviceData.split(SharedPreferencesManager.pairsSeparator);
            for (int i = 0; i < fields.length; i++) {
                String[] keyValue = fields[i].split(SharedPreferencesManager.keyValueSeparator);
                if (keyValue[0].equals(SharedPreferencesManager.deviceFriendlyName)) {
                    if (newDevice)// announcement name is the updated one
                        d.setFriendlyName(keyValue[1]);
                } else if (keyValue[0].equals(SharedPreferencesManager.deviceIconUrl)) {
                    if (keyValue[1] != null && !keyValue[1].isEmpty() && !keyValue[1].equals("null")) {
                        d.setStoredIconUrl(keyValue[1]);
                    }

                }
            }

            if (newDevice) {
                d.setStatus(DeviceStatus.UNAVAILABLE);
                if (deviceId != null) {
                    m_deviceList.put(deviceId.toString(), d);
                }
            }
        }
    }

    public void onDeviceLost(String serviceName, boolean verified) {
        DeviceImpl device = (DeviceImpl) getDeviceByServiceName(serviceName);
        Log.d(TAG, "onDeviceLost busName = " + serviceName + (device != null ? ", friendly name = " + device.getFriendlyName() : " device not found")
                + ", verified = " + verified);
        if (device != null) {
            if (verified) {

                Log.d(TAG, "onDeviceLost verified. busName = " + serviceName + ", friendly name = " + device.getFriendlyName());
                UUID deviceId = device.getId();
                removeDevice(device.getId());
                updateTheUi(IntentActions.ACTION_DEVICE_LOST, deviceId);
            } else {
                Log.d(TAG, "onDeviceLost lostAdvertisedName, not verified. busName = " + serviceName + ", friendly name = " + device.getFriendlyName());
            }
        }
    }

    public void onDeviceAlive(String busName) {
        DeviceImpl device = (DeviceImpl) getDeviceByServiceName(busName);

        Log.d(TAG, "onDeviceAlive busName = " + busName + (device != null ? ", friendly name = " + device.getFriendlyName() : " device not found"));
        if (device != null) {
            Log.d(TAG, "onDeviceAlive device is alive busName = " + busName + ", friendly name = " + device.getFriendlyName());
            device.setStatus(DeviceStatus.AVAILABLE);
            updateTheUi(IntentActions.ACTION_DEVICE_FOUND, device.getId());
        } else {
            Log.w(TAG, "onDeviceAlive found no device for busName: " + busName);
        }
    }

    public void onAnnouncement(String serviceName, short port, BusObjectDescription[] objectDescriptions, Map<String, Variant> serviceMetadata) {
        Map<String, Object> newMap = null;
        try {
            newMap = TransportUtil.fromVariantMap(serviceMetadata);
        } catch (BusException e) {
            e.printStackTrace();
        }
        if (newMap == null)
            return;

        UUID uniqueId = (UUID) newMap.get(AboutKeys.ABOUT_APP_ID);
        if (uniqueId == null) {
            Log.e(TAG, "onAnnouncement: received null device uuid!! ignoring.");
            return;
        }
        String friendlyName = String.valueOf(newMap.get(AboutKeys.ABOUT_DEVICE_NAME));
        String defaultLanguage = String.valueOf(newMap.get(AboutKeys.ABOUT_DEFAULT_LANGUAGE));

        String helpPage = (String) newMap.get("SupportUrl");

        DeviceImpl device;

        Log.i(TAG, "onAnnouncement: Receive announcment from device " + uniqueId + "Name = " + friendlyName);
        device = (DeviceImpl) getDevice(uniqueId);
        if (device == null) {
            Log.i(TAG, "onAnnouncement: can't find device by uuid -- " + uniqueId + "create new device");
            device = new DeviceImpl(serviceName, uniqueId);
            device.setPort(port);
            m_deviceList.put(uniqueId.toString(), device);
        }
        device.setServiceName(serviceName);
        device.setAbout(newMap);
        device.setFriendlyName(friendlyName);
        device.setInterfaces(objectDescriptions);
        device.setDirty();
        device.setPort(port);
        device.setDefaultLanguage(defaultLanguage);
        device.setHelpUrl(helpPage);

        device.setStatus(DeviceStatus.AVAILABLE);
    }

    // ******************** Update the ui
    protected void updateTheUi(String refreshType) {
        updateTheUi(refreshType, null, null);
    }

    protected void updateTheUi(String refreshType, UUID m_uniqueId) {
        updateTheUi(refreshType, null, m_uniqueId);
    }

    protected void updateTheUi(String refreshType, String msg, UUID m_uniqueId) {
        Intent intent = new Intent(refreshType);
        if (msg != null && msg.length() > 0) {
            intent.putExtra(IntentExtraKeys.MSG, msg);
        }
        if (m_uniqueId != null) {
            intent.putExtra(IntentExtraKeys.DEVICE_ID, m_uniqueId);
        }

        String deviceName = "";
        Device d = getDevice(m_uniqueId);
        if (d != null)
            deviceName = d.getFriendlyName();
        Log.i(TAG, "Send intent: Action = " + refreshType + ((m_uniqueId != null) ? " UUID = " + m_uniqueId : "")
                + (!deviceName.isEmpty() ? " device name = " + deviceName : "") + ((msg != null && !msg.isEmpty()) ? " msg = " + msg : ""));

        m_context.sendBroadcast(intent);
    }

    protected void updateTheUi(String refreshType, Bundle extras) {
        Intent intent = new Intent(refreshType);
        if (extras != null && !extras.isEmpty()) {
            intent.putExtras(extras);
        }

        Log.i(TAG, "Send intent: Action = " + refreshType + (extras != null ? Util.bundleToString(extras) : ""));

        m_context.sendBroadcast(intent);
    }

    // ***********************************************

    // NOTE: added for demo only
    public void addDevice(DeviceImpl d) {
        m_deviceList.put(d.getId().toString(), d);
    }

    @Override
    public boolean isDeviceUsingDefaultImage(UUID deviceID) {
        return m_ImageCacheManager.isDeviceUsingDefaultImage(deviceID);
    }

    private enum ImageCacheObjectState {
        GET_ICON_URL, GET_CONTENT_ICON
    };

    @Override
    public Bitmap getDeviceImage(UUID deviceID, boolean isNotificationWithImage, int idOfLayoutContainingIconView) {
        Device device = getDevice(deviceID);
        if (device != null) {
            if (device.getStoredIconUrl() == null) {
                m_ImageCacheManager.addNewDeviceToQueue(deviceID, isNotificationWithImage, idOfLayoutContainingIconView);
            } else {
                if (device.getStoredIconUrl().equals(ABOUT_ICON_DEFAULT_URL)) {
                    return defaultImage;
                } else {

                    String md5String = Util.calcMD5FromString(device.getStoredIconUrl());
                    String md5name = "i_" + (md5String != null ? md5String.toLowerCase() : "");
                    int imageID = m_context.getResources().getIdentifier(md5name, "drawable", m_context.getPackageName());
                    if (imageID > 0) {
                        Bitmap b = BitmapFactory.decodeResource(m_context.getResources(), imageID);
                        if (b != null) {
                            return b;
                        }
                    }
                    String resStringIdentifier = org.alljoyn.ioe.notificationviewer.logic.Util.getDensityName(m_context);
                    String imageInSD = Environment.getExternalStorageDirectory().getAbsolutePath() + Constants.ALLJOYN_DIR + resStringIdentifier + "/"
                            + md5name + ".png";
                    File imgFile = new File(imageInSD);
                    if (imgFile.exists()) {
                        Bitmap b = BitmapFactory.decodeFile(imageInSD);
                        if (b != null) {
                            return b;
                        }
                    }
                }// not ABOUT_ICON_DEFAULT_URL
            }// URL exist ;
        }// device !=null
        return defaultImage;
    }

    private class ImageCacheManager {

        private class ImageCacheObject {

            public ImageCacheObject(UUID uuid, boolean isNotificationWithImage, int idOfLayoutContainingIconView) {
                m_UUID = uuid;
                m_LastTimeTried = 0;
                m_Count = 0;
                m_IsUsingDefalutImage = true;
                m_ImageCacheObjectState = ImageCacheObjectState.GET_ICON_URL;
                this.isNotificationWithImage = isNotificationWithImage;
                this.idOfLayoutContainingIconView = idOfLayoutContainingIconView;
            }

            public UUID m_UUID;
            public long m_LastTimeTried;
            public int m_Count;
            public boolean m_IsUsingDefalutImage;
            public ImageCacheObjectState m_ImageCacheObjectState;
            public boolean isNotificationWithImage;
            public int idOfLayoutContainingIconView;
        }

        private Map<UUID, ImageCacheObject> m_UUIDtoImageCacheObject = new HashMap<UUID, ImageCacheObject>();

        private BlockingQueue<ImageCacheObject> m_imageDownloadQueue = new ArrayBlockingQueue<ImageCacheObject>(100);
        private Map<String, Set<UUID>> m_URLtoUUIDSet = new HashMap<String, Set<UUID>>();

        public void removeDeviceFromUUUIDList(UUID uuid) {
            m_UUIDtoImageCacheObject.remove(uuid);
        }

        public void addNewDeviceToQueue(UUID uuid, boolean isNotificationWithImage, int idOfLayoutContainingIconView) {
            if (!isInQueue(uuid)) {
                ImageCacheObject cacheObject = new ImageCacheObject(uuid, isNotificationWithImage, idOfLayoutContainingIconView);
                m_UUIDtoImageCacheObject.put(uuid, cacheObject);
                m_imageDownloadQueue.add(cacheObject);
            }
        }

        public boolean isInQueue(UUID m_UUID) {
            return (m_UUIDtoImageCacheObject.get(m_UUID) != null);
        }

        public boolean isDeviceUsingDefaultImage(UUID deviceID) {
            // if not found return true (UI will draw default image)
            ImageCacheObject cacheObject = m_UUIDtoImageCacheObject.get(deviceID);
            if (cacheObject == null)
                return true;

            return cacheObject.m_IsUsingDefalutImage;

        }

        private boolean checkIfUrlResourceAvilable(String url) {
            boolean retval = false;
            String md5String = Util.calcMD5FromString(url);
            String md5name = "i_" + (md5String != null ? md5String.toLowerCase() : "");
            int imageID = m_context.getResources().getIdentifier(md5name, "drawable", m_context.getPackageName());
            boolean hasImageId = imageID > 0;
            if (hasImageId) {
                retval = true;
            } else {
                String resStringIdentifier = org.alljoyn.ioe.notificationviewer.logic.Util.getDensityName(m_context);
                String imageInSD = Environment.getExternalStorageDirectory().getAbsolutePath() + Constants.ALLJOYN_DIR + resStringIdentifier + "/" + md5name
                        + ".png";
                File imgFile = new File(imageInSD);
                // check if exist in SD card
                if (imgFile.exists()) {
                    retval = true;
                }
            }
            return retval;
        }

        public void init() {
            new Thread("ImageCacheManagerThread") {

                @Override
                public void run() {
                    while (m_isRunning) {
                        try {
                            final ImageCacheObject cacheObject = m_imageDownloadQueue.take();
                            final Device device = getDevice(cacheObject.m_UUID);
                            if (device == null) {
                                continue;
                            }

                            if (cacheObject.m_ImageCacheObjectState == ImageCacheObjectState.GET_ICON_URL) {
                                Log.d(TAG, "ImageCacheManagerThread GET_ICON_URL  " + device.getId());
                                DeviceResponse response = device.getIconUrl();
                                if (response.getStatus() == ResponseCode.Status_OK) {
                                    Log.d(TAG, "ImageCacheManagerThread GET_ICON_URL  " + device.getId() + " was successful");
                                    if (response.getMsg() != null && !response.getMsg().isEmpty()) {
                                        device.setStoredIconUrl(response.getMsg());
                                        Log.d(TAG, "ImageCacheManagerThread GET_ICON_URL  " + device.getId() + " url " + response.getMsg());
                                        if (!checkIfUrlResourceAvilable(response.getMsg())) {
                                            Log.d(TAG, "ImageCacheManagerThread icon not in device ask service to bring " + response.getMsg());

                                            Bundle extras = new Bundle();
                                            extras.putString(BackgroundService.BUNDLE_IMAGE_URL, response.getMsg());
                                            extras.putString(BackgroundService.BUNDLE_IMAGE_DIMENSION,
                                                    org.alljoyn.ioe.notificationviewer.logic.Util.getDensityName(DeviceManagerImpl.m_context));
                                            Intent downloadImageIntent = new Intent(DeviceManagerImpl.m_context, BackgroundService.class);
                                            downloadImageIntent.setAction(BackgroundService.GET_IMAGE_ACTION);
                                            downloadImageIntent.putExtras(extras);
                                            downloadImageIntent.putExtra(IntentActions.EXTRA_VIEW_ID, cacheObject.idOfLayoutContainingIconView);
                                            downloadImageIntent.putExtra(IntentActions.EXTRA_IS_NOTIFICATION_WITH_IMAGE, cacheObject.isNotificationWithImage);
                                            DeviceManagerImpl.m_context.startService(downloadImageIntent);
                                            Set<UUID> set = m_URLtoUUIDSet.get(response.getMsg());
                                            if (set == null) {
                                                m_URLtoUUIDSet.put(response.getMsg(), new HashSet<UUID>(Arrays.asList(device.getId())));
                                            } else {
                                                set.add(device.getId());
                                                m_URLtoUUIDSet.put(response.getMsg(), set);
                                            }
                                        } else {
                                            Log.d(TAG, "ImageCacheManagerThread  resource is available for " + device.getId()
                                                    + "send AJ_ON_DEVICE_ICON_AVAILABLE");
                                            // updateTheUi(IntentActions.AJ_ON_DEVICE_ICON_AVAILABLE,device.getId());

                                            if (NotificationViewer.IGNORE_VIEW_ID != cacheObject.idOfLayoutContainingIconView) {
                                                Intent intent = new Intent(IntentActions.ACTION_TVNOTIFICATION_VIEWER_ICON_AVAILABLE);
                                                intent.putExtra(IntentActions.EXTRA_APP_ID, device.getId());
                                                if (false == cacheObject.isNotificationWithImage) {
                                                    // HACK: need to pass id of layout containing the icon view
                                                    intent.putExtra(IntentActions.EXTRA_VIEW_ID, cacheObject.idOfLayoutContainingIconView);
                                                }
                                                intent.putExtra(IntentActions.EXTRA_IS_NOTIFICATION_WITH_IMAGE, cacheObject.isNotificationWithImage);
                                                m_context.sendBroadcast(intent);
                                            }
                                        }
                                    } else {
                                        Log.d(TAG, "ImageCacheManagerThread GET_ICON_URL  " + device.getId() + " is null ask to fetch GET_CONTENT_ICON");
                                        cacheObject.m_ImageCacheObjectState = ImageCacheObjectState.GET_CONTENT_ICON;
                                        m_imageDownloadQueue.put(cacheObject);
                                    }
                                } else {
                                    // some error occurred.
                                    cacheObject.m_LastTimeTried = System.currentTimeMillis();
                                    cacheObject.m_Count = cacheObject.m_Count + 1;
                                    if (cacheObject.m_Count < 100) {
                                        Log.e(TAG, "ImageCacheManagerThread GET_ICON_URL " + device.getId() + " failed  try later");
                                        new Timer().schedule(new TimerTask() {
                                            @Override
                                            public void run() {
                                                m_imageDownloadQueue.add(cacheObject);
                                            }
                                        }, 5 * 1000);
                                    } else {
                                        Log.e(TAG, "ImageCacheManagerThread GET_ICON_URL " + device.getId() + " failed  more then 100 time stop");
                                    }

                                }
                            }// if (cacheObject.m_ImageCacheObjectState==ImageCacheObjectState.GET_ICON_URL)
                            else if (cacheObject.m_ImageCacheObjectState == ImageCacheObjectState.GET_CONTENT_ICON) {
                                Log.d(TAG, "ImageCacheManagerThread GET_CONTENT_ICON " + device.getId());
                                DeviceResponse response = device.getDeviceIconContent();
                                if (response.getStatus() == ResponseCode.Status_OK) {
                                    Log.d(TAG, "ImageCacheManagerThread GET_CONTENT_ICON " + device.getId() + " successful  url generated" + response.getMsg());
                                    device.setStoredIconUrl(response.getMsg());
                                    // updateTheUi(IntentActions.AJ_ON_DEVICE_ICON_AVAILABLE,device.getId());

                                    Intent intent = new Intent(IntentActions.ACTION_TVNOTIFICATION_VIEWER_ICON_AVAILABLE);
                                    intent.putExtra(IntentActions.EXTRA_APP_ID, device.getId());
                                    if (false == cacheObject.isNotificationWithImage) {
                                        // HACK: need to pass id of layout containing the icon view
                                        intent.putExtra(IntentActions.EXTRA_VIEW_ID, cacheObject.idOfLayoutContainingIconView);
                                    }
                                    intent.putExtra(IntentActions.EXTRA_IS_NOTIFICATION_WITH_IMAGE, cacheObject.isNotificationWithImage);
                                    m_context.sendBroadcast(intent);
                                } else {

                                    cacheObject.m_LastTimeTried = System.currentTimeMillis();
                                    cacheObject.m_Count = cacheObject.m_Count + 1;
                                    if (cacheObject.m_Count < 50) {
                                        Log.e(TAG, "ImageCacheManagerThread GET_CONTENT_ICON " + device.getId() + "failed  try later");
                                        new Timer().schedule(new TimerTask() {
                                            @Override
                                            public void run() {
                                                m_imageDownloadQueue.add(cacheObject);
                                            }
                                        }, 30 * 1000);

                                    } else {
                                        Log.e(TAG, "ImageCacheManagerThread GET_CONTENT_ICON " + device.getId() + "failed  more then 50 time stop");
                                    }

                                }
                            }

                            // }// end of else
                        }// end of try

                        catch (InterruptedException e) {
                            Writer writer = new StringWriter();
                            PrintWriter printWriter = new PrintWriter(writer);
                            e.printStackTrace(printWriter);
                            Log.e(TAG, "ImageCacheManagerThread " + writer.toString());
                        }
                    }// while
                }// end of run
            }.start();
        }

        public ImageCacheManager() {
            Log.i(TAG, "ImageCacheManager Constructor");
            m_context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action != null) {
                        if (action.equals(BackgroundService.IMAGE_RETRIEVED_ACTION)) {
                            Log.i(TAG, "ImageCacheManager BroadcastReceiver  got IMAGE_RETRIEVED_ACTION ");
                            Bundle extra = intent.getExtras();
                            if (extra != null) {
                                String url = extra.getString(BackgroundService.BUNDLE_IMAGE_URL);
                                boolean isNotificationWithImage = extra.getBoolean(IntentActions.EXTRA_IS_NOTIFICATION_WITH_IMAGE);
                                int idOfLayoutContainingIconView = extra.getInt(IntentActions.EXTRA_APP_ID);
                                Log.i(TAG, "ImageCacheManager BroadcastReceiver  got IMAGE_RETRIEVED_ACTION " + url);
                                Set<UUID> set = m_URLtoUUIDSet.get(url);
                                if (set != null) {
                                    for (UUID uuid : set) {
                                        Log.i(TAG, "ImageCacheManager BroadcastReceiver Traversing via " + url + " UUID " + uuid.toString());
                                        Intent iconAvailableIntent = new Intent(IntentActions.ACTION_TVNOTIFICATION_VIEWER_ICON_AVAILABLE);
                                        iconAvailableIntent.putExtra(IntentActions.EXTRA_APP_ID, uuid);
                                        if (false == isNotificationWithImage) {
                                            // HACK: need to pass id of layout containing the icon view
                                            iconAvailableIntent.putExtra(IntentActions.EXTRA_VIEW_ID, idOfLayoutContainingIconView);
                                        }
                                        iconAvailableIntent.putExtra(IntentActions.EXTRA_IS_NOTIFICATION_WITH_IMAGE, isNotificationWithImage);
                                        m_context.sendBroadcast(iconAvailableIntent);
                                    }
                                }
                            }
                        }
                    }
                }
            }, new IntentFilter(BackgroundService.IMAGE_RETRIEVED_ACTION));

        }

    }// ImageCacheManager
}
