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

import java.util.UUID;

import org.alljoyn.ioe.notificationviewer.logic.DeviceManagerImpl;
import org.alljoyn.ioe.notificationviewer.logic.Interface.Device;

import android.graphics.Bitmap;
import android.widget.ImageView;

public class UIUtil {

    public static void setDeviceIcon(ImageView view, UUID deviceId, boolean isNotificationWithImage, int idOfLayoutContainingIconView) {
        Device device = getDevice(deviceId);
        if (device != null) {
            Bitmap b = DeviceManagerImpl.getInstance().getDeviceImage(deviceId, isNotificationWithImage, idOfLayoutContainingIconView);
            if (b != null) {
                view.setImageBitmap(b);
            } else {
                view.setImageResource(R.drawable.my_devices_icon_reg);
            }
        } else {
            view.setImageResource(R.drawable.my_devices_icon_reg);
        }
    }

    public static Device getDevice(UUID uuid) {
        return DeviceManagerImpl.getInstance().getDevice(uuid);
    }

}
