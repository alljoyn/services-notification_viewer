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

public class IntentActions {

    // FOR INTENTS
    public static final String ACTION_REFRESH = "org.alljoyn.ioe.android.new_data_action";

    public static final String ACTION_DEVICE_FOUND = "org.alljoyn.ioe.device_found";
    public static final String ACTION_DEVICE_LOST = "org.alljoyn.ioe.device_lost";
    public static final String ACTION_NEW_NOTIFICATION_ARRIVED = "org.alljoyn.ioe.new_notification_arrived";
    public static final String ACTION_NEW_NOTIFICATION_REMOVED = "org.alljoyn.ioe.new_notification_removed";

    public static final String ACTION_TVNOTIFICATION_VIEWER_ICON_AVAILABLE = "org.alljoyn.ioe.notificationviewer.device_icon_available";
    public static final String EXTRA_APP_ID = "appId";
    public static final String EXTRA_IS_NOTIFICATION_WITH_IMAGE = "isNotificationWithImage";
    public static final String EXTRA_VIEW_ID = "viewId";

    public static final String ACTION_SESSION_LOST_WITH_DEVICE = "org.alljoyn.ioe.session_lost_with_device";

    public static final String ACTION_DEVICE_STATUS_CHANGED = "org.alljoyn.ioe.device_status_state";
}
