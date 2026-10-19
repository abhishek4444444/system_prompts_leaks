package in.sarkarialert.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Built-in AI assistant powered by the user's own free Google AI Studio
 * (Gemini) API key. Opens with the tapped alert or browsed page as context;
 * can explain, summarize, answer in Hindi and even build an HTML infographic.
 */
public class AiChatActivity extends Activity {

    private static class Msg {
        String role;   // "user" or "ai"
        String text;
        boolean html;
        Bitmap img;    // generated image payload
        Msg(String role, String text, boolean html) {
            this.role = role; this.text = text; this.html = html;
        }
    }

    private final List<Msg> msgs = new ArrayList<Msg>();
    private ChatAdapter adapter;
    private ListView list;
    private EditText input;
    private android.widget.ImageView sendIcon;
    private View setup, chatArea;
    private boolean busy;
    private volatile boolean imgCancelled;
    private Bitmap pendingSave;

    private Alert alert;          // context: alert from a notification
    private String ctxUrl, ctxTitle, ctxSnippet; // context: page from the browser

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_ai);

        long id = getIntent().getLongExtra("alert_id", -1);
        if (id > 0) alert = new Db(this).get(id);
        ctxUrl = getIntent().getStringExtra("ctx_url");
        ctxTitle = getIntent().getStringExtra("ctx_title");
        ctxSnippet = getIntent().getStringExtra("ctx_snippet");

        list = (ListView) findViewById(R.id.ai_list);
        input = (EditText) findViewById(R.id.ai_input);
        sendIcon = (android.widget.ImageView) findViewById(R.id.ai_send_icon);
        setup = findViewById(R.id.ai_setup);
        chatArea = findViewById(R.id.ai_chat);
        adapter = new ChatAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.ai_skills).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(AiChatActivity.this, SkillsActivity.class));
            }
        });
        findViewById(R.id.ai_new).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Llm.cancel();
                ImageGen.cancel();
                imgCancelled = true;
                msgs.clear();
                busy = false;
                String hello = alert != null ? getString(R.string.ai_hello_alert)
                        : ctxUrl != null ? getString(R.string.ai_hello_page)
                        : getString(R.string.ai_hello);
                msgs.add(new Msg("ai", hello, false));
                sendIcon.setImageResource(R.drawable.ic_send);
                adapter.notifyDataSetChanged();
            }
        });

        TextView ctx = (TextView) findViewById(R.id.ai_ctx);
        if (alert != null) {
            ctx.setText(alert.title);
            TextView det = (TextView) findViewById(R.id.ai_detail);
            det.setVisibility(View.VISIBLE);
            det.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent i = new Intent(AiChatActivity.this, DetailActivity.class);
                    i.putExtra("id", alert.id);
                    startActivity(i);
                }
            });
        } else if (ctxUrl != null) {
            ctx.setText(ctxTitle == null ? ctxUrl : ctxTitle);
        }

        String key = Prefs.apiKey(this);
        if (key.length() > 0) startChat();
        else showSetup();

        String prefill = getIntent().getStringExtra("prefill");
        if (prefill != null && prefill.length() > 0) input.setText(prefill);
    }

    private void showSetup() {
        setup.setVisibility(View.VISIBLE);
        chatArea.setVisibility(View.GONE);
        final EditText keyIn = (EditText) findViewById(R.id.ai_key);
        keyIn.setText(Prefs.apiKey(this));
        findViewById(R.id.ai_get_key).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://aistudio.google.com/app/apikey")));
                } catch (Exception e) {
                    Toast.makeText(AiChatActivity.this,
                            R.string.error_no_url, Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.ai_save).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // strip stray spaces/newlines/quotes people pick up while copying
                String k = keyIn.getText().toString().replaceAll("\\s", "").replace("\"", "");
                if (k.length() < 20) {
                    Toast.makeText(AiChatActivity.this,
                            R.string.ai_key_bad, Toast.LENGTH_SHORT).show();
                    return;
                }
                Prefs.setApiKey(AiChatActivity.this, k);
                startChat();
            }
        });
    }

    private void startChat() {
        setup.setVisibility(View.GONE);
        chatArea.setVisibility(View.VISIBLE);
        if (msgs.size() == 0) {
            String hello = alert != null ? getString(R.string.ai_hello_alert)
                    : ctxUrl != null ? getString(R.string.ai_hello_page)
                    : getString(R.string.ai_hello);
            msgs.add(new Msg("ai", hello, false));
        }
        buildChips();
        findViewById(R.id.ai_send).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (busy) { Llm.cancel(); ImageGen.cancel(); imgCancelled = true; }
                else send(input.getText().toString());
            }
        });
        adapter.notifyDataSetChanged();
    }

    private void buildChips() {
        LinearLayout box = (LinearLayout) findViewById(R.id.ai_chips);
        box.removeAllViews();
        String[] labels = {getString(R.string.ai_quick_what), getString(R.string.ai_quick_explain),
                getString(R.string.ai_quick_summary), getString(R.string.ai_quick_info),
                getString(R.string.ai_quick_hindi), getString(R.string.ai_quick_local),
                getString(R.string.ai_image_chip)};
        for (String label : labels) {
            TextView chip = new TextView(this);
            chip.setText(label);
            chip.setTextSize(12);
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
            chip.setTextColor(0xFF63707F);
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setPadding((int) dp(14), (int) dp(7), (int) dp(14), (int) dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = (int) dp(8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String t = ((TextView) v).getText().toString();
                    if (t.equals(getString(R.string.ai_quick_local))) sendLocal();
                    else if (t.equals(getString(R.string.ai_image_chip))) sendImage();
                    else send(t);
                }
            });
            box.addView(chip);
        }
    }

    private void send(String text) {
        final String question = text == null ? "" : text.trim();
        if (question.length() == 0 || busy) return;
        // "create an image of ..." typed naturally → straight to the image
        // generator, never to the text LLM (it would only refuse)
        if (isImageRequest(question)) {
            input.setText("");
            generateImage(cleanImagePrompt(question), question);
            return;
        }
        input.setText("");
        msgs.add(new Msg("user", question, false));
        adapter.notifyDataSetChanged();
        sendIcon.setImageResource(R.drawable.ic_stop);

        final String key = Prefs.apiKey(this);
        if (key.length() == 0) {
            // no cloud key — the LOCAL brain answers instantly, offline
            msgs.add(new Msg("ai", OfflineBrain.answer(question, alert, this), false));
            sendIcon.setImageResource(R.drawable.ic_send);
            adapter.notifyDataSetChanged();
            list.smoothScrollToPosition(msgs.size() - 1);
            return;
        }
        final Msg typing = new Msg("ai", getString(R.string.ai_thinking), false);
        msgs.add(typing);
        adapter.notifyDataSetChanged();
        list.smoothScrollToPosition(msgs.size() - 1);
        busy = true;

        List<String[]> hist = new ArrayList<String[]>();
        int from = Math.max(0, msgs.size() - 2 - 16);
        for (int i = from; i < msgs.size() - 1; i++) {
            hist.add(new String[]{msgs.get(i).role, msgs.get(i).text});
        }
        final String asked = question;
        Llm.ask(key, buildSystem(), hist, new Llm.StreamCb() {
            @Override public void onDelta(final String soFar) {
                typing.text = soFar;
                adapter.notifyDataSetChanged();
            }
            @Override public void done(String reply, String err) {
                busy = false;
                msgs.remove(typing);
                sendIcon.setImageResource(R.drawable.ic_send);
                if (Llm.wasStopped(err)) {
                    msgs.add(new Msg("ai", "⏹ Stopped.", false));
                } else if (err != null) {
                    msgs.add(new Msg("ai", err, false));
                } else {
                    String clean = stripFences(reply);
                    msgs.add(new Msg("ai", clean, isHtml(clean)));
                    // teach the local Brain: plain-text answers become permanent memory
                    if (!isHtml(clean) && clean.length() > 20 && clean.length() < 2000) {
                        Prefs.learn(AiChatActivity.this, asked, clean);
                    }
                }
                adapter.notifyDataSetChanged();
                list.smoothScrollToPosition(msgs.size() - 1);
            }
        });
    }

    /** ⚡ local chip: instant on-device answer, no key, no internet */
    private void sendLocal() {
        if (busy) return;
        String q = input.getText().toString().trim();
        if (q.length() == 0) q = "summary";
        input.setText("");
        msgs.add(new Msg("user", q, false));
        msgs.add(new Msg("ai", OfflineBrain.answer(q, alert, this), false));
        adapter.notifyDataSetChanged();
        list.smoothScrollToPosition(msgs.size() - 1);
    }

    /** 🎨 chip: generate an image from whatever is typed */
    private void sendImage() {
        if (busy) return;
        final String prompt = input.getText().toString().trim();
        if (prompt.length() == 0) {
            Toast.makeText(this, R.string.ai_image_desc, Toast.LENGTH_SHORT).show();
            return;
        }
        input.setText("");
        generateImage(cleanImagePrompt(prompt), "🎨 " + prompt);
    }

    /** does this plain-typed message ask for an image? (no chip needed) */
    static boolean isImageRequest(String raw) {
        if (raw == null) return false;
        String q = raw.trim().toLowerCase(java.util.Locale.US);
        if (q.length() == 0) return false;
        if (q.startsWith("🎨")) return true;
        if (q.startsWith("/image") || q.startsWith("/imagine")) return true;
        String[] strong = {
            "generate image", "generate an image", "generate a image",
            "create image", "create an image", "create a image",
            "make image", "make an image", "make a image",
            "generate picture", "create picture", "make picture",
            "generate photo", "create photo", "make photo",
            "image banao", "image bana do", "tasveer banao", "tasvir banao",
            "photo banao", "picture banao", "drawing banao",
            "text to image", "txt2img"
        };
        for (int i = 0; i < strong.length; i++) {
            if (q.contains(strong[i])) return true;
        }
        boolean hasKind = q.contains("image of") || q.contains("picture of")
                || q.contains("photo of") || q.contains("drawing of")
                || q.contains("painting of") || q.contains("illustration of")
                || q.contains("poster of");
        boolean hasVerb = q.contains("create") || q.contains("generate")
                || q.contains("make") || q.contains("draw") || q.contains("paint")
                || q.contains("banao") || q.contains("show me") || q.contains("i want");
        if (hasKind && hasVerb) return true;
        if (q.startsWith("draw ") || q.startsWith("paint ") || q.startsWith("sketch ")) return true;
        return false;
    }

    /** strip the "create an image of ..." wrapper, keep the actual scene */
    static String cleanImagePrompt(String raw) {
        String p = raw == null ? "" : raw.trim();
        String low = p.toLowerCase(java.util.Locale.US);
        if (low.startsWith("🎨")) {
            p = p.substring("🎨".length()).trim();
            low = p.toLowerCase(java.util.Locale.US);
        }
        String[] pre = {
            "please create an image of", "please create a image of",
            "please generate an image of", "please generate a image of",
            "please make an image of", "please make a image of",
            "create an image of", "create a image of", "create image of",
            "generate an image of", "generate a image of", "generate image of",
            "make an image of", "make a image of", "make image of",
            "create a picture of", "generate a picture of", "make a picture of",
            "create a photo of", "generate a photo of", "make a photo of",
            "draw a picture of", "draw an image of", "draw a image of",
            "draw ", "paint ", "sketch ",
            "create an image", "create a image", "create image",
            "generate an image", "generate a image", "generate image",
            "make an image", "make a image", "make image",
            "image banao", "tasveer banao", "photo banao",
            "please create", "please ",
            "/imagine", "/image", "image:", "image of"
        };
        for (int i = 0; i < pre.length; i++) {
            if (low.startsWith(pre[i])) {
                p = p.substring(pre[i].length()).trim();
                break;
            }
        }
        String low2 = p.toLowerCase(java.util.Locale.US);
        String[] suf = {"ki image banao", "image banao", "ki tasveer banao",
                "tasveer banao", "photo banao", "picture banao"};
        for (int i = 0; i < suf.length; i++) {
            if (low2.endsWith(suf[i])) {
                p = p.substring(0, p.length() - suf[i].length()).trim();
                break;
            }
        }
        if (p.endsWith(".")) p = p.substring(0, p.length() - 1).trim();
        return p.length() < 3 ? (raw == null ? "" : raw.trim()) : p;
    }

    /** 🎨 text-to-image: several providers, live status and real error details */
    private void generateImage(final String prompt, String display) {
        if (busy) return;
        msgs.add(new Msg("user", display, false));
        final Msg work = new Msg("ai", getString(R.string.ai_image_gen), false);
        msgs.add(work);
        adapter.notifyDataSetChanged();
        list.smoothScrollToPosition(msgs.size() - 1);
        busy = true;
        imgCancelled = false;
        ImageGen.begin();
        sendIcon.setImageResource(R.drawable.ic_stop);
        final String storedKeys = Prefs.apiKey(this);
        new Thread(new Runnable() {
            @Override public void run() {
                final StringBuilder errors = new StringBuilder();
                Bitmap found = ImageGen.generate(storedKeys, prompt,
                        new ImageGen.Progress() {
                            @Override public void update(final String message) {
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if (!imgCancelled && msgs.contains(work)) {
                                            work.text = message;
                                            adapter.notifyDataSetChanged();
                                        }
                                    }
                                });
                            }
                        }, errors);
                final Bitmap fb = found;
                final String fe = errors.length() > 0 ? errors.toString()
                        : "• No image provider returned a usable image.";
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        sendIcon.setImageResource(R.drawable.ic_send);
                        if (imgCancelled || ImageGen.isCancelled()) {
                            work.text = "⏹ Stopped.";
                        } else if (fb != null) {
                            work.text = "🎨 Here's your image:";
                            Msg im = new Msg("ai", "[generated image]", false);
                            im.img = fb;
                            int pos = msgs.indexOf(work);
                            if (pos >= 0) msgs.add(pos + 1, im);
                        } else {
                            work.text = getString(R.string.ai_image_fail, fe);
                        }
                        adapter.notifyDataSetChanged();
                        if (msgs.size() > 0) {
                            list.smoothScrollToPosition(msgs.size() - 1);
                        }
                    }
                });
            }
        }).start();
    }

    /** ⬇ Save a generated image into Pictures/SarkariAlert. */
    private void saveGeneratedImage(Bitmap bmp) {
        if (bmp == null) return;
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            pendingSave = bmp;
            requestPermissions(new String[]{
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 72);
            Toast.makeText(this, R.string.ai_image_permission, Toast.LENGTH_SHORT).show();
            return;
        }
        doSaveGeneratedImage(bmp);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 72) return;
        Bitmap bmp = pendingSave;
        pendingSave = null;
        if (bmp != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            doSaveGeneratedImage(bmp);
        } else {
            Toast.makeText(this, R.string.ai_image_permission, Toast.LENGTH_SHORT).show();
        }
    }

    private void doSaveGeneratedImage(final Bitmap bmp) {
        new Thread(new Runnable() {
            @Override public void run() {
                Uri inserted = null;
                String savedWhere = "";
                Exception failure = null;
                try {
                    String filename = "SarkariAlert-IMG-"
                            + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                                    .format(new Date()) + ".png";
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                        values.put("relative_path",
                                Environment.DIRECTORY_PICTURES + "/SarkariAlert");
                        values.put("is_pending", 1);
                        inserted = getContentResolver().insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                        if (inserted == null) {
                            throw new Exception("MediaStore did not create an image entry.");
                        }
                        OutputStream out = getContentResolver().openOutputStream(inserted);
                        if (out == null) throw new Exception("Could not open the image file.");
                        try {
                            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                                throw new Exception("Could not encode the PNG image.");
                            }
                        } finally {
                            out.close();
                        }
                        ContentValues done = new ContentValues();
                        done.put("is_pending", 0);
                        getContentResolver().update(inserted, done, null, null);
                        savedWhere = "Pictures/SarkariAlert/" + filename;
                    } else {
                        File dir = new File(Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES), "SarkariAlert");
                        if (!dir.exists() && !dir.mkdirs()) {
                            throw new Exception("Could not create the Pictures/SarkariAlert folder.");
                        }
                        File file = new File(dir, filename);
                        FileOutputStream out = new FileOutputStream(file);
                        try {
                            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                                throw new Exception("Could not encode the PNG image.");
                            }
                        } finally {
                            out.close();
                        }
                        MediaScannerConnection.scanFile(AiChatActivity.this,
                                new String[]{file.getAbsolutePath()},
                                new String[]{"image/png"}, null);
                        savedWhere = file.getAbsolutePath();
                    }
                } catch (Exception e) {
                    failure = e;
                    if (inserted != null) {
                        try { getContentResolver().delete(inserted, null, null); }
                        catch (Exception ignored) { }
                    }
                }

                final String where = savedWhere;
                final Exception fe = failure;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (fe == null) {
                            Toast.makeText(AiChatActivity.this,
                                    getString(R.string.ai_image_saved, where),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            String message = fe.getMessage() == null
                                    ? fe.getClass().getSimpleName() : fe.getMessage();
                            Toast.makeText(AiChatActivity.this,
                                    getString(R.string.ai_image_save_failed, message),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    /** light markdown: bold, bullets, headings — rendered in the bubbles */
    @SuppressWarnings("deprecation")
    private static CharSequence md(String t) {
        if (t == null) return "";
        String s = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        s = s.replaceAll("(?m)^#{1,6}\\s*(.+)$", "<b>$1</b>");
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        s = s.replaceAll("(?m)^\\s*[*\\-•]\\s+", "&bull; ");
        s = s.replaceAll("`(.+?)`", "$1");
        s = s.replace("\n", "<br>");
        return Html.fromHtml(s);
    }

    private String buildSystem() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the built-in AI assistant inside the 'Sarkari Alert' Android app, ");
        sb.append("which tracks Indian government job notifications, exams, forms and schemes. ");
        sb.append("Help the user understand such notifications. Be accurate and concise; prefer short bullet points. ");
        sb.append("Never invent dates, numbers or links; if unsure, say so and advise checking the official notification. ");
        sb.append("If the user writes in Hindi or asks for Hindi, reply in Hindi (Devanagari script). ");
        sb.append("Otherwise reply in simple English. ");
        sb.append("Special rule: if the user asks for an infographic, diagram, poster or visual summary, reply with ONE ");
        sb.append("complete self-contained HTML document starting with <!DOCTYPE html>, using ONLY inline CSS ");
        sb.append("(no external files, no JavaScript): dark navy background #0B1E33, accent color #F4772E, ");
        sb.append("white text, card-based grid, big readable headings, in the user's preferred language. ");
        sb.append("For every normal answer reply with plain text only — never HTML. ");
        sb.append("If the user asks for a generated photo/image/drawing, do NOT say you cannot create images — ");
        sb.append("this app has a built-in image generator: tell them to just send a message like ");
        sb.append("'Create an image of ...' and the app itself will generate the picture.");
        if (alert != null) {
            sb.append("\n\nCONTEXT — the user is asking about this alert:");
            sb.append("\nTitle: ").append(alert.title);
            if (alert.org.length() > 0) sb.append("\nOrganisation: ").append(alert.org);
            if (alert.posts.length() > 0) sb.append("\nPosts: ").append(alert.posts);
            if (alert.lastDate.length() >= 10) sb.append("\nLast date: ").append(alert.lastDate);
            if (alert.qualification.length() > 0) sb.append("\nQualification: ").append(alert.qualification);
            if (alert.fee.length() > 0) sb.append("\nApplication fee: ").append(alert.fee);
            if (alert.details.length() > 0) sb.append("\nDetails: ").append(alert.details);
            if (alert.url.length() > 0) sb.append("\nLink: ").append(alert.url);
        }
        if (ctxUrl != null && ctxUrl.length() > 0) {
            sb.append("\n\nCONTEXT — the user is browsing this page:");
            sb.append("\nTitle: ").append(ctxTitle == null ? "" : ctxTitle);
            sb.append("\nURL: ").append(ctxUrl);
            if (ctxSnippet != null && ctxSnippet.length() > 0) {
                String s = ctxSnippet.length() > 1200 ? ctxSnippet.substring(0, 1200) : ctxSnippet;
                sb.append("\nPage excerpt: ").append(s);
            }
        }
        String skills = Prefs.activeSkillsForPrompt(this);
        if (skills.length() > 0) sb.append("\n\n").append(skills);
        return sb.toString();
    }

    static String stripFences(String t) {
        if (t == null) return "";
        String s = t.trim();
        if (s.startsWith("```")) {
            int first = s.indexOf('\n');
            if (first > 0) s = s.substring(first + 1);
            int last = s.lastIndexOf("```");
            if (last > 0) s = s.substring(0, last);
        }
        return s.trim();
    }

    static boolean isHtml(String t) {
        String s = t == null ? "" : t.trim().toLowerCase();
        return s.startsWith("<!doctype") || s.startsWith("<html") || s.startsWith("<svg")
                || (s.contains("<div") && s.contains("</div>"));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private class ChatAdapter extends BaseAdapter {
        @Override public int getCount() { return msgs.size(); }
        @Override public Object getItem(int position) { return msgs.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public int getViewTypeCount() { return 4; }
        @Override public int getItemViewType(int position) {
            Msg m = msgs.get(position);
            if (m.role.equals("user")) return 0;
            if (m.img != null) return 3;
            return m.html ? 2 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Msg m = msgs.get(position);
            View v = convertView;
            int type = getItemViewType(position);
            if (v == null) {
                if (type == 0) v = getLayoutInflater().inflate(R.layout.item_msg_user, parent, false);
                else if (type == 1) v = getLayoutInflater().inflate(R.layout.item_msg_ai, parent, false);
                else if (type == 3) v = getLayoutInflater().inflate(R.layout.item_msg_img, parent, false);
                else v = getLayoutInflater().inflate(R.layout.item_msg_info, parent, false);
            }
            if (type == 1) {
                TextView tv = (TextView) v.findViewById(R.id.m_text);
                tv.setText(md(m.text));
                // long-press any AI reply to copy it (ChatGPT-style)
                v.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View b) {
                        try {
                            android.content.ClipboardManager cm =
                                (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                            cm.setText(m.text);
                            Toast.makeText(AiChatActivity.this,
                                    R.string.ai_copied, Toast.LENGTH_SHORT).show();
                        } catch (Exception ignored) { }
                        return true;
                    }
                });
            } else if (type == 3) {
                android.widget.ImageView imageView =
                        (android.widget.ImageView) v.findViewById(R.id.m_img);
                imageView.setImageBitmap(m.img);
                View.OnClickListener download = new View.OnClickListener() {
                    @Override public void onClick(View b) {
                        saveGeneratedImage(m.img);
                    }
                };
                imageView.setOnClickListener(download);
                View downloadButton = v.findViewById(R.id.m_download);
                if (downloadButton != null) downloadButton.setOnClickListener(download);
            } else if (type == 0) {
                ((TextView) v.findViewById(R.id.m_text)).setText(m.text);
            } else {
                Button view = (Button) v.findViewById(R.id.m_view);
                view.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View b) {
                        Intent i = new Intent(AiChatActivity.this, InfographicActivity.class);
                        i.putExtra("html", m.text);
                        startActivity(i);
                    }
                });
            }
            return v;
        }
    }
}
