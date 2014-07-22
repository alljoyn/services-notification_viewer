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

package org.alljoyn.ioe.notificationviewer.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.alljoyn.ioe.notificationviewer.Constants;

import android.os.Environment;

public class PropertiesManagerImpl implements PropertiesManager {

    private final static String PROPERTIES_FILEPATH = Environment.getExternalStorageDirectory().getAbsolutePath() + Constants.ALLJOYN_DIR + Constants.PROPERTIES_FILENAME;
    private final static String KEY_NOTIF_POPUP_TIMEOUT_IN_MS = "notif_popup_timeout_in_ms";
    private final static String KEY_NOTIF_FULLSCREEN_ALERT_TIMEOUT_IN_MS = "notif_fullscreen_alert_timeout_in_ms";
    
    private final static String DEFAULT_VALUE_NOTIF_POPUP_TIMEOUT_IN_MS = "45000";
    private final static String DEFAULT_VALUE_NOTIF_FULLSCREEN_ALERT_TIMEOUT_IN_MS = "10000";
    
    private Properties properties;
    
    @Override
    public void init() {
        properties = new Properties();
        File f = new File(PROPERTIES_FILEPATH);
        if (false == f.isFile()) {
            FileOutputStream out = null;
            try {
                properties.put(KEY_NOTIF_POPUP_TIMEOUT_IN_MS, DEFAULT_VALUE_NOTIF_POPUP_TIMEOUT_IN_MS);
                properties.put(KEY_NOTIF_FULLSCREEN_ALERT_TIMEOUT_IN_MS, DEFAULT_VALUE_NOTIF_FULLSCREEN_ALERT_TIMEOUT_IN_MS);
                
                File parentDir = f.getParentFile();
                if (null != parentDir && false == parentDir.isDirectory()) {
                    parentDir.mkdirs();
                }
                out = new FileOutputStream(PROPERTIES_FILEPATH);
                properties.store(out, null);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (null != out) {
                    try {
                        out.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        else {
            FileInputStream in = null;
            try {
                in = new FileInputStream(PROPERTIES_FILEPATH);
                properties.load(in);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (null != in) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public int getNotificationPopupTimeoutInMs() {
        int retval = Integer.parseInt(DEFAULT_VALUE_NOTIF_POPUP_TIMEOUT_IN_MS);
        String valueNotifPopupTimeoutInSeconds = (String) properties.get(KEY_NOTIF_POPUP_TIMEOUT_IN_MS);
        if (null != valueNotifPopupTimeoutInSeconds) {
            try {
                retval = Integer.parseInt(valueNotifPopupTimeoutInSeconds);
            }
            catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
        }
        return retval;
    }

    @Override
    public int getNotificationFullscreenAlertTimeoutInMs() {
        int retval = Integer.parseInt(DEFAULT_VALUE_NOTIF_FULLSCREEN_ALERT_TIMEOUT_IN_MS);
        String valueNotifPopupTimeoutInSeconds = (String) properties.get(KEY_NOTIF_FULLSCREEN_ALERT_TIMEOUT_IN_MS);
        if (null != valueNotifPopupTimeoutInSeconds) {
            try {
                retval = Integer.parseInt(valueNotifPopupTimeoutInSeconds);
            }
            catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
        }
        return retval;
    }

}
