package au.ghosthunter.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
    private WebView web;
    private ValueCallback<Uri[]> pending;

    static final String FIX_CORNER_BAT =
            "(function(){var b=document.getElementById('ghostBatCorner');if(b)b.remove();})();";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xFF14181D);
        setContentView(web);

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setBuiltInZoomControls(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // On a phone the flapping bat in the corner sits on top of the buttons,
                // so the mobile app leaves it out completely.
                view.evaluateJavascript(FIX_CORNER_BAT, null);
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
}
