package au.ghosthunter.mobile;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import android.view.WindowManager;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ghosthunter Mobile: the Ghost Hunter 5.1 web app (assets/index.html) in its own
 * Android window. Everything runs on the phone; nothing is sent anywhere.
 */
public class MainActivity extends Activity {
    private static final int PICK_FILES = 1;
    private static final int SAVE_FILE = 2;
    private static final int NOTE_ID = 51;
    private static final String CHANNEL = "counting";
    private String saveText, saveName;
    private boolean counting;
    private WebView web;
    private ValueCallback<Uri[]> pending;

    private String mobileFixes = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xFF14181D);
        setContentView(web);

        mobileFixes = readAsset("mobile_fixes.js");

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setBuiltInZoomControls(false);
        // "Share report" button: hand the text report to email / messages / Drive.
        web.addJavascriptInterface(new ShareBridge(), "GhostHunterAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Phone-only tweaks (assets/mobile_fixes.js): no corner bat, reliable
                // video playback, and a clear message instead of a stuck Count button.
                view.evaluateJavascript(mobileFixes, null);
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri u = Uri.parse(url);
                if ("file".equals(u.getScheme()) || "blob".equals(u.getScheme())
                        || "data".equals(u.getScheme())) {
                    return false;
                }
                // Outside links open in the phone's browser, not inside the app.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pending != null) {
                    pending.onReceiveValue(null);
                }
                pending = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                String[] mimes = mimeTypes(params.getAcceptTypes());
                if (mimes.length > 0) {
                    pick.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
                }
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(Intent.createChooser(pick, "Choose file"), PICK_FILES);
                } catch (Exception e) {
                    pending = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });

        if (state != null) {
            web.restoreState(state);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
    }

    private String readAsset(String name) {
        try (java.io.InputStream in = getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (java.io.IOException e) {
            return "";
        }
    }

    /** Turns the page's accept="..." list (".wav,audio/wav", "video/*,.mp4") into MIME types. */
    static String[] mimeTypes(String[] accept) {
        Set<String> out = new LinkedHashSet<>();
        if (accept != null) {
            for (String group : accept) {
                if (group == null) continue;
                for (String a : group.split(",")) {
                    a = a.trim().toLowerCase();
                    if (a.isEmpty()) continue;
                    if (a.contains("/")) {
                        out.add(a);
                    } else if (a.equals(".wav")) {
                        out.add("audio/wav");
                        out.add("audio/x-wav");
                        out.add("audio/*");
                    } else if (a.equals(".mp4") || a.equals(".m4v")) {
                        out.add("video/mp4");
                    } else if (a.equals(".mov")) {
                        out.add("video/quicktime");
                    } else if (a.equals(".webm")) {
                        out.add("video/webm");
                    }
                }
            }
        }
        return out.toArray(new String[0]);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == SAVE_FILE) {
            boolean ok = false;
            if (result == RESULT_OK && data != null && data.getData() != null && saveText != null) {
                try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    out.write(saveText.getBytes("UTF-8"));
                    ok = true;
                } catch (Exception ignored) {
                }
            }
            String name = saveName == null ? "" : saveName.replace("\\", "").replace("'", "");
            saveText = null;
            web.evaluateJavascript("window.ghSaved && ghSaved(" + ok + ", '" + name + "')", null);
            return;
        }
        if (request != PICK_FILES || pending == null) {
            super.onActivityResult(request, result, data);
            return;
        }
        Uri[] uris = null;
        if (result == RESULT_OK && data != null) {
            ArrayList<Uri> list = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    list.add(clip.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                list.add(data.getData());
            }
            if (!list.isEmpty()) {
                uris = list.toArray(new Uri[0]);
            }
        }
        pending.onReceiveValue(uris);
        pending = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("deprecation")
    private void showNote(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Counting progress", NotificationManager.IMPORTANCE_LOW));
            }
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        b.setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Ghost Hunter is counting").setContentText(text)
                .setOngoing(true).setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        try {
            nm.notify(NOTE_ID, b.build());
        } catch (Exception ignored) {
        }
    }

    private class ShareBridge {
        /** Counting: keep the screen on and show progress in a notification. */
        @JavascriptInterface
        public void counting(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    counting = on;
                    if (on) {
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3);
                        }
                        showNote("Counting bats…");
                    } else {
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                        if (nm != null) nm.cancel(NOTE_ID);
                    }
                }
            });
        }

        @JavascriptInterface
        public void progress(final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (counting) showNote(text);
                }
            });
        }

        /** Save a report / CSV / labels file where the user chooses (Downloads, Drive, a USB drive...). */
        @JavascriptInterface
        public void saveFile(final String name, final String mime, final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    saveText = text;
                    saveName = name;
                    Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    save.addCategory(Intent.CATEGORY_OPENABLE);
                    save.setType(mime);
                    save.putExtra(Intent.EXTRA_TITLE, name);
                    try {
                        startActivityForResult(save, SAVE_FILE);
                    } catch (Exception e) {
                        saveText = null;
                        web.evaluateJavascript("window.ghSaved && ghSaved(false)", null);
                    }
                }
            });
        }

        @JavascriptInterface
        public void share(final String title, final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_SUBJECT, title);
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(send, title));
                }
            });
        }
    }
}
