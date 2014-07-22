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

import java.util.Map;
import java.util.UUID;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class SharedPreferencesManager {
    private static SharedPreferences devicesSp;
    private static String DEVICES = "devices";

    public static final String keyValueSeparator = "=";
    public static final String pairsSeparator = ";";
    // Device constants
    public static final String deviceSsid = "SSID";
    public static final String deviceId = "ID";
    public static final String deviceFriendlyName = "friendlyName";
    public static final String deviceIconUrl = "iconUrl";

    private final static String TAG = "SharedPreferencesManager";

    public static void init(Context c) {
        devicesSp = c.getSharedPreferences(DEVICES, Context.MODE_PRIVATE);
    }

    public static void saveDevice(UUID m_uniqueId, String deviceDescription) {
        if (m_uniqueId == null || deviceDescription == null)
            return;
        Log.d(TAG, "save device to preferencese, id = " + m_uniqueId + " device description = " + deviceDescription);
        if (devicesSp == null) {
            Log.e(TAG, "devicesSp == null");
            return;
        }
        Editor e = devicesSp.edit();

        e.putString(m_uniqueId.toString(), deviceDescription);
        e.commit();
    }

    public static void removeDevice(UUID m_uniqueId) {
        Log.d(TAG, "remove device from preferencese, id = " + m_uniqueId);
        if (devicesSp == null) {
            Log.e(TAG, "devicesSp == null");
            return;
        }
        Editor e = devicesSp.edit();

        e.remove(m_uniqueId.toString());
        e.commit();
    }

    public static void updateDevice(UUID m_uniqueId, String deviceNewDescription) {
        Log.d(TAG, "update device in preferencese, id = " + m_uniqueId + " device new  description = " + deviceNewDescription);
        if (devicesSp == null) {
            Log.e(TAG, "devicesSp == null");
            return;
        }
        Editor e = devicesSp.edit();

        e.remove(m_uniqueId.toString());
        e.putString(m_uniqueId.toString(), deviceNewDescription);

        e.commit();
    }

    public static Map<String, String> getAllDevices() {
        if (devicesSp == null)
            return null;
        return (Map<String, String>) devicesSp.getAll();
    }
}
