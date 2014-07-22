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

package org.alljoyn.ioe.notificationviewer.logic.Interface;

import java.util.Map;
import java.util.UUID;

import org.alljoyn.about.transport.AboutTransport;
import org.alljoyn.about.transport.IconTransport;

// if the device has not been on boarded, this is the actual device (Coffee machine, toaster oven etc.)
// if the device has on boarded,this is the application that runs on the machine.
// (machine can have more than one application running on it) 
public interface Device {

    public enum IconDataRequestType {
        EMPTY("EMPTY"), SDCARD("SDCARD"), RESOURCE("RESOURCE"), DEVICE_CONTENT("DEVICE_CONTENT");

        private String brandname;

        private IconDataRequestType(String brand) {
            this.brandname = brand;
        }

        @Override
        public String toString() {
            return brandname;
        }
    }

    public enum DeviceStatus {
        AVAILABLE, UNAVAILABLE,
    }

    public static String DEVICE_TAG_LAST_ACTION = "device_tag_last_action";

    public enum DeviceAction {
        // About
        GET_ABOUT,
        // About Icon
        GET_ICON_MIME_TYPE, GET_ICON_URL, GET_ICON_SIZE,
    }

    public enum ServiceType {
        ABOUT(AboutTransport.INTERFACE_NAME), ABOUT_ICON(IconTransport.INTERFACE_NAME), NOTIFICATION("org.alljoyn.Notification");

        private String m_interface = "";

        private ServiceType(String interfaceName) {
            m_interface = interfaceName;
        }

        public String getInterface() {
            return m_interface;
        }

    }// enum

    // Tag manage
    public Object getTag(String key);

    public void setTag(String key, Object value);

    public Map<String, Object> getAllTags();

    public boolean hasTag(String key);

    public void removeTag(String key);

    public UUID getId();

    public void setDefaultLanguage(String l);

    public String getDefaultLanguage();

    public String getFriendlyName();

    public DeviceStatus getStatus();

    public void setHelpUrl(String l);

    public String getHelpURL();

    public void turnOnNotifications();

    public void turnOffNotifications();

    public boolean isNotificationOn();

    public Map<String, Object> getAbout(String language, boolean force);

    public int getIconSize();

    public DeviceResponse getIconUrl();

    public DeviceResponse getDeviceIconContent();

    public String getStoredIconUrl();

    public void setStoredIconUrl(String url);

    public String getIconMimeType();

    boolean isServiceSupported(ServiceType service);

};
