TVNotificationViewer
====================

TVNotificationViewer is an Android app, intended for larger screens, which displays AllJoyn notifications.

The app will display most notifications will appear in the bottom left corner, and they will bubble up as new notifications appear.   A certain type of notification, sent with message type 'EMERGENCY' and with a 'richIconUrl', will be displayed using the downloaded image as a full-screen image along with the notification text.

The length of time that these notifications are displayed is configurable via /mnt/sdcard/AllJoyn/TVNotificationViewer.properties - defaults are:

        notif_popup_timeout_in_ms=45000
        notif_fullscreen_alert_timeout_in_ms=10000

The required libs can be downloaded from "https://allseenalliance.org/source-code".   Download following Android zips:

  1) "Core SDK - release"
  2) "Notification SDK"

Extract the zips, retrieve the jars and .so files, and place them in the TVNotificationViewer's "libs" dir, such that it contains:

  /libs/armeabi/liballjoyn_java.so
  /libs/alljoyn_about.jar
  /libs/alljoyn.jar
  /libs/NotificationService.jar
  /libs/NSCommons.jar
  /libs/NSNativePlatformAndr.jar
