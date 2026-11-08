package au.ghosthunter.mobile;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import android.view.WindowManager;
import android.content.ClipData;
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.MediaStore;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.content.SharedPreferences;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ghosthunter Mobile: the Ghost Hunter 5.1 web app (assets/index.html) in its own
 * Android window. Everything runs on the phone; nothing is sent anywhere.
 */
public class MainActivity extends Activity {
    private static final int PICK_FILES = 1;
    private static final int PICK_DRIVE = 3;
    /** Where each chosen video came from (file name -> its address), so a report can go beside it. */
    private final HashMap<String, Uri> videoFrom = new HashMap<>();
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

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("ghosthunter", MODE_PRIVATE);
    }

    /** The drive folder the user allowed once, if Android still lets us write to it. */
    private Uri driveTree() {
        String t = prefs().getString("driveTree", null);
        if (t == null) return null;
        Uri tree = Uri.parse(t);
        for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(tree) && p.isWritePermission()) return tree;
        }
        return null;
    }

    private static String volumeOf(String docId) {
        int i = docId.indexOf(':');
        return i < 0 ? "" : docId.substring(0, i);
    }

    /** Write a report next to the video on its drive (or into the allowed drive folder). */
    private String writeToDrive(String video, String name, String mime, String text) throws Exception {
        Uri tree = driveTree();
        if (tree == null) return "pick";
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        String dirId = treeId;
        Uri v = video == null ? null : videoFrom.get(video);
        if (v != null && DocumentsContract.isDocumentUri(this, v) && tree.getAuthority().equals(v.getAuthority())) {
            String vid = DocumentsContract.getDocumentId(v);
            int slash = vid.lastIndexOf('/');
            String parent = slash > 0 ? vid.substring(0, slash) : (vid.indexOf(':') >= 0 ? vid.substring(0, vid.indexOf(':') + 1) : null);
            String treePath = treeId.endsWith("/") || treeId.endsWith(":") ? treeId : treeId + "/";
            if (parent != null && volumeOf(vid).equals(volumeOf(treeId))
                    && ((parent + "/").startsWith(treePath) || parent.equals(treeId) || treeId.endsWith(":"))) {
                dirId = parent;
            }
        }
        Uri dir = DocumentsContract.buildDocumentUriUsingTree(tree, dirId);
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId);
        Uri target = null;
        try (Cursor c = getContentResolver().query(kids, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (name.equals(c.getString(1))) {
                    target = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
                    break;
                }
            }
        }
        if (target == null) target = DocumentsContract.createDocument(getContentResolver(), dir, mime, name);
        if (target == null) return "";
        try (java.io.OutputStream out = getContentResolver().openOutputStream(target, "wt")) {
            out.write(text.getBytes("UTF-8"));
        }
        String where = dirId.indexOf(':') >= 0 ? dirId.substring(dirId.indexOf(':') + 1) : dirId;
        return "the footage drive" + (where.isEmpty() ? "" : " › " + where.replace("/", " › "));
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == PICK_DRIVE) {
            boolean ok = false;
            if (result == RESULT_OK && data != null && data.getData() != null) {
                try {
                    getContentResolver().takePersistableUriPermission(data.getData(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    prefs().edit().putString("driveTree", data.getData().toString()).apply();
                    ok = true;
                } catch (Exception ignored) {
                }
            }
            web.evaluateJavascript("window.ghDrivePicked && ghDrivePicked(" + ok + ")", null);
            return;
        }
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
                for (Uri u : list) {
                    String n = displayName(u);
                    if (n != null) videoFrom.put(n, u);
                }
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
        b.setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Ghost Hunter: Bat Counter is counting").setContentText(text)
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

        /**
         * Save a report straight onto the phone, with no questions: Download/Ghost Hunter on
         * Android 10+, so the footage drive is only ever read. The same file name is overwritten
         * when the bats are checked again. Returns where it went, or "" if it failed.
         */
        @JavascriptInterface
        public String autoSave(String name, String mime, String text) {
            try {
                byte[] bytes = text.getBytes("UTF-8");
                if (Build.VERSION.SDK_INT >= 29) {
                    String rel = "Download/Ghost Hunter/";
                    Uri coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    Uri target = null;
                    try (Cursor c = getContentResolver().query(coll, new String[]{MediaStore.MediaColumns._ID},
                            MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                            new String[]{rel, name}, null)) {
                        if (c != null && c.moveToFirst()) target = Uri.withAppendedPath(coll, String.valueOf(c.getLong(0)));
                    }
                    if (target == null) {
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                        v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
                        target = getContentResolver().insert(coll, v);
                    }
                    if (target == null) return "";
                    try (java.io.OutputStream out = getContentResolver().openOutputStream(target, "wt")) {
                        out.write(bytes);
                    }
                    return "Download/Ghost Hunter";
                }
                java.io.File dir = getExternalFilesDir("Ghost Hunter");
                if (dir == null) return "";
                dir.mkdirs();
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(dir, name))) {
                    out.write(bytes);
                }
                return "Android/data/au.ghosthunter.mobile/files/Ghost Hunter";
            } catch (Exception e) {
                return "";
            }
        }

        /** Save beside the footage. Returns where it went, "pick" if no drive folder is allowed yet, or "" on failure. */
        @JavascriptInterface
        public String driveSave(String video, String name, String mime, String text) {
            try {
                return writeToDrive(video, name, mime, text);
            } catch (Exception e) {
                return "";
            }
        }

        /** Ask once for the footage drive (the user taps the drive, then "Use this folder"). */
        @JavascriptInterface
        public void pickDriveFolder() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    try {
                        startActivityForResult(pick, PICK_DRIVE);
                    } catch (Exception e) {
                        web.evaluateJavascript("window.ghDrivePicked && ghDrivePicked(false)", null);
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
