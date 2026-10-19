package in.sarkarialert.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
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

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

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
                if (busy) Llm.cancel();
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
        input.setText("");
        msgs.add(new Msg("user", question, false));
        adapter.notifyDataSetChanged();
        sendIcon.setImageResource(R.drawable.ic_stop);

        final String key = Prefs.apiKey(this);
        if (key.length() == 0) {
            // no cloud key — the LOCAL brain answers instantly, offline
            msgs.add(new Msg("ai", OfflineBrain.answer(question, alert, this), false));
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

    /** 🎨 text-to-image via Pollinations (free Flux model, no key needed) */
    private void sendImage() {
        if (busy) return;
        final String prompt = input.getText().toString().trim();
        if (prompt.length() == 0) {
            Toast.makeText(this, R.string.ai_image_desc, Toast.LENGTH_SHORT).show();
            return;
        }
        input.setText("");
        msgs.add(new Msg("user", "🎨 " + prompt, false));
        final Msg work = new Msg("ai", getString(R.string.ai_image_gen), false);
        msgs.add(work);
        adapter.notifyDataSetChanged();
        list.smoothScrollToPosition(msgs.size() - 1);
        busy = true;
        sendIcon.setImageResource(R.drawable.ic_stop);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String url = "https://image.pollinations.ai/prompt/"
                            + URLEncoder.encode(prompt, "UTF-8")
                            + "?width=768&height=768&nologo=true&model=flux";
                    final byte[] bytes = WebApi.getBytes(url);
                    final Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            busy = false;
                            sendIcon.setImageResource(R.drawable.ic_send);
                            if (bmp == null) {
                                work.text = getString(R.string.ai_image_fail, "bad image data");
                            } else {
                                work.text = null;
                                work.img = bmp;
                            }
                            adapter.notifyDataSetChanged();
                            list.smoothScrollToPosition(msgs.size() - 1);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            busy = false;
                            sendIcon.setImageResource(R.drawable.ic_send);
                            work.text = getString(R.string.ai_image_fail,
                                    e.getMessage() == null ? "network error" : e.getMessage());
                            adapter.notifyDataSetChanged();
                        }
                    });
                }
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
        sb.append("For every normal answer reply with plain text only — never HTML.");
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
                ((android.widget.ImageView) v.findViewById(R.id.m_img)).setImageBitmap(m.img);
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
