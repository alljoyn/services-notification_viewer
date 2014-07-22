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
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.alljoyn.about.AboutServiceImpl;
import org.alljoyn.about.client.AboutClient;
import org.alljoyn.about.icon.AboutIconClient;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.Status;
import org.alljoyn.ioe.notificationviewer.Constants;
import org.alljoyn.ioe.notificationviewer.logic.Interface.Device;
import org.alljoyn.ioe.notificationviewer.logic.Interface.DeviceManager;
import org.alljoyn.ioe.notificationviewer.logic.Interface.DeviceResponse;
import org.alljoyn.ioe.notificationviewer.logic.Interface.DeviceResponse.ResponseCode;
import org.alljoyn.ioe.notificationviewer.logic.Interface.IntentActions;
import org.alljoyn.ioe.notificationviewer.logic.Interface.IntentExtraKeys;
import org.alljoyn.services.common.BusObjectDescription;
import org.alljoyn.services.common.ClientBase;
import org.alljoyn.services.common.ServiceAvailabilityListener;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

// if the device has not been on boarded, this is the actual device (Coffee machine, toaster oven etc.)
// if the device has on boarded,this is the application that runs on the machine.
// (machine can have more than one application running on it) 
public class DeviceImpl implements Device, ServiceAvailabilityListener {

    private final String TAG = "DeviceImpl";
    UUID m_uniqueId;// from the about //-----
    String m_friendlyName = "";// ----
    DeviceStatus m_status;// ??

    Map<String, Object> m_tag;// should contain all ui extras (muteDeviceNotifications etc)
    Map<String, Object> m_about;// info from the about interface

    BusObjectDescription[] m_interfaces = null;//

    String m_serviceName; // will be changed every time the device bus connect to the daemon

    private String m_defaultLanguage = "en";
    private short m_port;

    private boolean m_notificationOn = true;
    private String m_helpUrl = "";
    private boolean m_aboutDirty;
    private AboutClient m_aboutClient;
    private AboutIconClient m_iconClient;

    private String m_storeAboutIconUrl = null;

    @Override
    public String getStoredIconUrl() {
        return m_storeAboutIconUrl;
    }

    @Override
    public void setStoredIconUrl(String url) {
        m_storeAboutIconUrl = url;
    }

    // for announcement
    public DeviceImpl(String serviceName, UUID uniqueId) {
        Log.i(TAG, "New Device " + serviceName + "," + uniqueId);
        m_serviceName = serviceName;
        m_aboutDirty = true;
        m_status = DeviceStatus.AVAILABLE;
        m_uniqueId = uniqueId;
    }

    @Override
    public UUID getId() {
        return m_uniqueId;
    }

    protected void setId(UUID uniqueId) {
        m_uniqueId = uniqueId;
    }

    public short getPort() {
        return m_port;
    }

    protected void setPort(short port) {
        m_port = port;
    }

    @Override
    public String getFriendlyName() {
        return m_friendlyName;
    }

    @Override
    public DeviceStatus getStatus() {
        return m_status;
    }

    @Override
    public Object getTag(String key) {
        if (m_tag == null)
            return null;
        return m_tag.get(key);
    }

    @Override
    public void setTag(String key, Object value) {
        if (m_tag == null) {
            m_tag = new HashMap<String, Object>();
        }
        m_tag.put(key, value);
    }

    @Override
    public Map<String, Object> getAllTags() {
        return m_tag;
    }

    @Override
    public boolean hasTag(String key) {
        if (m_tag == null)
            return false;
        return m_tag.containsKey(key);
    }

    @Override
    public void removeTag(String key) {
        if (m_tag != null) {
            m_tag.remove(key);
        }
    }

    @Override
    public Map<String, Object> getAbout(String language, boolean force) {
        Log.d(TAG, "getAbout " + toLogString());

        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.GET_ABOUT);
        if (force || m_aboutDirty) // request new about.
        {
            if (m_aboutClient == null) {
                try {
                    if (m_serviceName == null)
                        return null;
                    m_aboutClient = AboutServiceImpl.getInstance().createAboutClient(m_serviceName, this, m_port);
                } catch (Exception e) {
                    Log.e(TAG, "getAbout: device " + m_friendlyName + "-ServiceName = " + m_serviceName, e);
                    return m_about;
                }
            }

            DeviceResponse status = connectToDevice(m_aboutClient);
            if (status.getStatus() != ResponseCode.Status_OK) {
                // ERROR
                Log.e(TAG, "getAbout: device " + m_friendlyName + "-ServiceName = " + m_serviceName);
                return m_about;
            }
            try {
                m_about = m_aboutClient.getAbout((language == null ? m_defaultLanguage : language));
            } catch (BusException e) {
                Log.e(TAG, "getAbout: device " + m_friendlyName + "-ServiceName = " + m_serviceName);
                e.printStackTrace();
            }
            m_aboutDirty = false;
        }
        disconnectFromDevice(m_aboutClient);
        return m_about;
    }

    protected BusObjectDescription[] getInterfaces() {
        return m_interfaces;
    }

    protected void setInterfaces(BusObjectDescription[] objectDescriptions) {
        m_interfaces = objectDescriptions;
    }

    @Override
    public boolean isServiceSupported(ServiceType service) {
        if (m_interfaces != null) {
            for (int i = 0; i < m_interfaces.length; i++) {
                String[] interfaces = m_interfaces[i].getInterfaces();
                if (interfaces.length == 0)
                    return false;

                for (int j = 0; j < interfaces.length; j++) {
                    String currentInterface = interfaces[j];
                    if (currentInterface.startsWith(service.getInterface()))
                        return true;
                }
            }
        }

        return false;

    }

    public String getServiceName() {
        return m_serviceName;
    }

    protected void setServiceName(String serviceName) {
        Log.i(TAG, "setServiceName: " + serviceName);
        m_serviceName = serviceName;
        resetServices();
    }

    @Override
    public String getHelpURL() {
        return m_helpUrl;
    }

    @Override
    public void turnOnNotifications() {
        m_notificationOn = true;
    }

    @Override
    public void turnOffNotifications() {
        m_notificationOn = false;
    }

    @Override
    public boolean isNotificationOn() {
        return m_notificationOn;
    }

    public void setStatus(DeviceStatus status) {
        if (status.equals(m_status))
            return;

        Log.i(TAG, "setStatus: set device status for " + toLogString() + ", old status = " + m_status + ", new status = " + status);
        m_status = status;

        Bundle extras = new Bundle();
        extras.putSerializable(IntentExtraKeys.DEVICE_ID, m_uniqueId);
        ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.ACTION_DEVICE_STATUS_CHANGED, extras);
    }

    public void setDirty() // Should rarely used by the UI!!
    {
        m_aboutDirty = true;
    }

    private DeviceResponse connectToDevice(ClientBase client) {
        Log.d(TAG, "connectToDevice " + toLogString());

        if (client == null) {
            return new DeviceResponse(ResponseCode.Status_ERROR, "fail connect to device, client == null");
        }
        if (client.isConnected()) {
            return new DeviceResponse(ResponseCode.Status_OK);
        }

        Status status = client.connect();
        Log.i(TAG,
                "connectToDevice: device " + m_friendlyName + "-ServiceName = " + m_serviceName + "-uniqueId = " + m_uniqueId + "-AJReturnStatus="
                        + status.name());

        switch (status) {
        case OK: {
            Log.d(TAG, "connectToDevice. Join Session OK");
            return new DeviceResponse(ResponseCode.Status_OK);
        }
        case ALLJOYN_JOINSESSION_REPLY_ALREADY_JOINED: {
            Log.d(TAG, "connectToDevice: Join Session returned ALLJOYN_JOINSESSION_REPLY_ALREADY_JOINED. Ignoring");
            return new DeviceResponse(ResponseCode.Status_OK);
        }
        case ALLJOYN_JOINSESSION_REPLY_FAILED:
        case ALLJOYN_JOINSESSION_REPLY_UNREACHABLE: {
            Log.e(TAG, "connectToDevice: Join Session returned ALLJOYN_JOINSESSION_REPLY_FAILED. Device is unavailable. Pseudo ping");
            return new DeviceResponse(ResponseCode.Status_ERROR_CANT_ESTABLISH_SESSION, "device unreachable");
        }
        default: {
            Log.e(TAG, "connectToDevice: Join session returned error: " + status.name());
            return new DeviceResponse(ResponseCode.Status_ERROR, "Failed connecting to device");

        }
        }
    }

    private void disconnectFromDevice(ClientBase client) {
        Log.d(TAG, "disconnectFromDevice " + toLogString());

        if (client != null) {
            Log.d(TAG, "DeviceImpl.disconnectFromDevice client is not null");
            if (client.isConnected()) {
                Log.d(TAG, "DeviceImpl.disconnectFromDevice client is connected. Disconnecting");
                client.disconnect();
            } else {
                Log.d(TAG, "DeviceImpl.disconnectFromDevice client is NOT connected. name = " + m_friendlyName);
            }
        }
    }

    // for recovery
    public void setFriendlyName(String name) {
        m_friendlyName = name;
    }

    protected void setAbout(Map<String, Object> serviceMetadata) {
        m_about = serviceMetadata;
    }

    private void saveDeviceToPreference() {
        Log.i(TAG, "SaveDeviceToPreference " + m_uniqueId);
        SharedPreferencesManager.saveDevice(m_uniqueId, getThinString());
    }

    private void removeDeviceFromPreference() {
        Log.i(TAG, "removeDeviceFromPreference " + m_uniqueId);
        SharedPreferencesManager.removeDevice(m_uniqueId);
    }

    private String getThinString() {
        StringBuilder thinDevice = new StringBuilder();
        thinDevice
                .append(SharedPreferencesManager.pairsSeparator + SharedPreferencesManager.deviceId + SharedPreferencesManager.keyValueSeparator + m_uniqueId);
        thinDevice.append(SharedPreferencesManager.pairsSeparator + SharedPreferencesManager.deviceFriendlyName + SharedPreferencesManager.keyValueSeparator
                + m_friendlyName);
        thinDevice.append(SharedPreferencesManager.pairsSeparator + SharedPreferencesManager.deviceIconUrl + SharedPreferencesManager.keyValueSeparator
                + m_storeAboutIconUrl);
        return thinDevice.toString();
    }

    /***************** ServiceAvailabilityListener ************/
    @Override
    public void connectionLost() {
        Log.i(TAG, "connectionLost with device " + m_friendlyName);
        ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.ACTION_SESSION_LOST_WITH_DEVICE, "", m_uniqueId);
    }

    /***************** ServiceAvailabilityListener ************/

    @Override
    public int getIconSize() {
        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.GET_ICON_SIZE);

        int size = -1;

        if (m_iconClient == null) {
            try {
                if (m_serviceName == null)
                    return size;
                m_iconClient = AboutServiceImpl.getInstance().createAboutIconClient(m_serviceName, this, m_port);
            } catch (Exception e) {
                Log.e(TAG, "getIconSize: device " + m_friendlyName + "-ServiceName = " + m_serviceName);
                e.printStackTrace();
                return size;
            }
        }
        if (connectToDevice(m_iconClient).getStatus() != ResponseCode.Status_OK) {
            // ERROR
            return size;
        }
        try {
            size = m_iconClient.getSize();
        } catch (BusException e) {
            Log.e(TAG, "getIconSize: device " + m_friendlyName + "-ServiceName = " + m_serviceName);
            e.printStackTrace();
        }
        return size;
    }

    @Override
    public DeviceResponse getIconUrl() {

        Log.i(TAG, "getIconUrl " + toLogString());

        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.GET_ICON_URL);
        if (isServiceSupported(Device.ServiceType.ABOUT_ICON)) {
            Log.i(TAG, "getIconUrl");
            if (m_iconClient == null) {
                try {
                    if (m_serviceName == null) {
                        Log.e(TAG, "getIconUrl deviceId=" + m_uniqueId + " service name is null");
                        return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME);
                    }
                    m_iconClient = AboutServiceImpl.getInstance().createAboutIconClient(m_serviceName, this, m_port);
                    if (m_iconClient == null)// check if m_iconClient is not null
                    {
                        return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME);
                    }
                } catch (Exception e) {

                    Writer writer = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(writer);
                    e.printStackTrace(printWriter);
                    Log.e(TAG, "getIconUrl deviceId=" + m_uniqueId + "   " + writer.toString());
                    return new DeviceResponse(ResponseCode.Status_ERROR);
                }
            }

            try {
                if (connectToDevice(m_iconClient).getStatus() != ResponseCode.Status_OK) {
                    Log.e(TAG, "getIconUrl deviceId=" + m_uniqueId + " failed to establish session");
                    return new DeviceResponse(ResponseCode.Status_ERROR_CANT_ESTABLISH_SESSION);
                }
                String url = m_iconClient.GetUrl();
                Log.e(TAG, "getIconUrl " + toLogString() + " returned url: " + url);
                return new DeviceResponse(ResponseCode.Status_OK, url);
            } catch (BusException e) {
                Writer writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer);
                e.printStackTrace(printWriter);
                Log.e(TAG, "getIconUrl deviceId=" + m_uniqueId + " " + writer.toString());
                return new DeviceResponse(ResponseCode.Status_ERROR);
            } catch (Exception e) {
                Writer writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer);
                e.printStackTrace(printWriter);
                Log.e(TAG, "getIconUrl deviceId=" + m_uniqueId + "   " + writer.toString());
                return new DeviceResponse(ResponseCode.Status_ERROR);
            }

        }
        return new DeviceResponse(ResponseCode.Status_ERROR);

    }

    @Override
    public String getIconMimeType() {
        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.GET_ICON_MIME_TYPE);
        if (m_iconClient == null) {
            try {
                if (m_serviceName == null)
                    return null;
                m_iconClient = AboutServiceImpl.getInstance().createAboutIconClient(m_serviceName, this, m_port);
            } catch (Exception e) {
                Log.e(TAG, "getIconMimeType: device " + m_friendlyName + "-ServiceName = " + m_serviceName);
                e.printStackTrace();
                return "";
            }
        }
        if (connectToDevice(m_iconClient).getStatus() != ResponseCode.Status_OK) {
            Log.e(TAG, "getIconMimeType: device " + m_friendlyName + "-ServiceName = " + m_serviceName);
            return "";
        }
        try {
            return m_iconClient.getMimeType();
        } catch (BusException e) {
            Log.e(TAG, "getIconMimeType: device " + m_friendlyName + "-ServiceName = " + m_serviceName);
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public DeviceResponse getDeviceIconContent() {
        Log.i(TAG, "getDeviceIconContent " + toLogString());

        if (isServiceSupported(Device.ServiceType.ABOUT_ICON)) {
            Log.i(TAG, "getDeviceIconContent isServiceSupported true");
            if (m_iconClient == null) {
                try {
                    if (m_serviceName == null) {
                        Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + " Service name is null");
                        return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME);
                    }
                    m_iconClient = AboutServiceImpl.getInstance().createAboutIconClient(m_serviceName, this, m_port);
                } catch (Exception e) {
                    Writer writer = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(writer);
                    e.printStackTrace(printWriter);
                    Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + "   " + writer.toString());
                    return new DeviceResponse(ResponseCode.Status_ERROR);
                }
            }
            FileOutputStream outStream = null;

            try {
                if (connectToDevice(m_iconClient).getStatus() != ResponseCode.Status_OK) {
                    Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + " failed to establish session");
                    return new DeviceResponse(ResponseCode.Status_ERROR_CANT_ESTABLISH_SESSION);
                }

                int size = m_iconClient.getSize();
                if (size > 0) {
                    Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + " content size is " + size);
                    byte[] iconContent = m_iconClient.GetContent();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(iconContent, 0, iconContent.length);
                    if (bitmap != null) {
                        String url = DeviceManager.ABOUT_ICON_LOCAL_PREFIX_URL + m_uniqueId.toString();
                        String md5String = Util.calcMD5FromString(url);
                        String md5name = "i_" + (md5String != null ? md5String.toLowerCase() : "");
                        String resStringIdentifier = org.alljoyn.ioe.notificationviewer.logic.Util.getDensityName(DeviceManagerImpl.m_context);
                        String imageInSD = Environment.getExternalStorageDirectory().getAbsolutePath() + Constants.ALLJOYN_DIR + resStringIdentifier + "/"
                                + md5name + ".png";

                        File file = new File(imageInSD);
                        if (!file.isFile()) {
                            File parentDir = new File(file.getAbsoluteFile().getParent());
                            if (!parentDir.exists()) {
                                parentDir.mkdirs();
                            }
                        }
                        outStream = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
                        outStream.flush();

                        Log.d(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + " url " + url + " resolved to " + imageInSD);
                        return new DeviceResponse(DeviceResponse.ResponseCode.Status_OK, url);
                    }

                } else {
                    Log.d(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + "content size is 0 revert to default image");
                    return new DeviceResponse(DeviceResponse.ResponseCode.Status_OK, DeviceManager.ABOUT_ICON_DEFAULT_URL);
                }
            } catch (BusException e) {
                Log.e(TAG, "requestIcon: device " + m_friendlyName + "-ServiceName = " + m_serviceName);
                Writer writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer);
                e.printStackTrace(printWriter);
                Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + " " + writer.toString());

            } catch (Exception e) {
                Writer writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer);
                e.printStackTrace(printWriter);
                Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + " " + writer.toString());
            } finally {
                if (outStream != null) {
                    if (outStream != null) {
                        try {
                            outStream.close();
                        } catch (Exception e) {
                            Writer writer = new StringWriter();
                            PrintWriter printWriter = new PrintWriter(writer);
                            e.printStackTrace(printWriter);
                            Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + " " + writer.toString());
                        }
                    }
                }
            }

        }
        return new DeviceResponse(DeviceResponse.ResponseCode.Status_ERROR);

    }

    @Override
    public void setHelpUrl(String l) {
        m_helpUrl = l;
    }

    @Override
    public void setDefaultLanguage(String l) {
        m_defaultLanguage = l;
    }

    @Override
    public String getDefaultLanguage() {
        return m_defaultLanguage;
    }

    public void resetServices() {
        Log.i("TAG", "resetServices ending all sessions of device: " + toLogString());
        if (m_aboutClient != null && m_aboutClient.isConnected()) {
            m_aboutClient.disconnect();
        }
        m_aboutClient = null;

        if (m_iconClient != null && m_iconClient.isConnected()) {
            m_iconClient.disconnect();
        }
        m_iconClient = null;

    }

    private String toLogString() {
        return String.format("[busName=%s, appId=%s, name=%s]", m_serviceName, m_uniqueId, m_friendlyName);
    }

}
