package in.sarkarialert.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Sarkari Brain — the app's OWN local AI with its own GUI.
 * Everything is on-device: the knowledge engine answers instantly and the
 * reply is revealed word-by-word with a thinking animation, so it feels
 * alive even though nothing ever leaves the phone.
 */
public class LocalAiActivity extends Activity {

    private static class Msg {
        String role; // "user" | "ai"
        String text;
        Msg(String role, String text) { this.role = role; this.text = text; }
    }

    private final List<Msg> msgs = new ArrayList<Msg>();
    private ChatAdapter adapter;
    private ListView list;
    private EditText input;
    private ImageView sendIcon;
    private boolean typingAnim;
    private Msg curWork;
    private String curFull;
    private Runnable revealRun;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_local_ai);

        list = (ListView) findViewById(R.id.brain_list);
        input = (EditText) findViewById(R.id.brain_input);
        sendIcon = (ImageView) findViewById(R.id.brain_send_icon);
        adapter = new ChatAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.brain_cloud).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(LocalAiActivity.this, AiChatActivity.class));
            }
        });
        findViewById(R.id.brain_new).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (revealRun != null) ui.removeCallbacks(revealRun);
                typingAnim = false;
                curWork = null;
                msgs.clear();
                msgs.add(new Msg("ai", OfflineBrain.greeting()));
                sendIcon.setImageResource(R.drawable.ic_send);
                adapter.notifyDataSetChanged();
            }
        });
        findViewById(R.id.brain_send).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (typingAnim) stopReveal();
                else send(input.getText().toString());
            }
        });

        buildChips();
        msgs.add(new Msg("ai", OfflineBrain.greeting()));
        adapter.notifyDataSetChanged();
    }

    private void buildChips() {
        android.widget.LinearLayout box = (android.widget.LinearLayout) findViewById(R.id.brain_chips);
        box.removeAllViews();
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        for (final String chip : OfflineBrain.quickChips()) {
            TextView t = new TextView(this);
            t.setText(chip);
            t.setTextSize(12);
            t.setTypeface(null, android.graphics.Typeface.BOLD);
            t.setTextColor(0xFF63707F);
            t.setBackgroundResource(R.drawable.bg_chip);
            t.setPadding(pad + pad / 2, pad / 2 + 2, pad + pad / 2, pad / 2 + 2);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = pad;
            t.setLayoutParams(lp);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (chip.equals("🎯 Skills")) {
                        startActivity(new Intent(LocalAiActivity.this, SkillsActivity.class));
                    } else {
                        send(chip.replaceAll("^[^ ]+ ", ""));
                    }
                }
            });
            box.addView(t);
        }
    }

    private void send(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.length() == 0 || typingAnim) return;
        input.setText("");
        msgs.add(new Msg("user", q));
        final Msg work = new Msg("ai", "");
        msgs.add(work);
        adapter.notifyDataSetChanged();
        list.smoothScrollToPosition(msgs.size() - 1);

        // instant on-device "thinking", then a word-by-word reveal
        final String answer = OfflineBrain.answer(q,
                getIntent().getLongExtra("alert_id", -1) > 0
                        ? new Db(this).get(getIntent().getLongExtra("alert_id", -1))
                        : null,
                this);
        typingAnim = true;
        sendIcon.setImageResource(R.drawable.ic_stop);
        // 1) animated dots
        final String[] dots = {"•", "• •", "• • •"};
        work.text = dots[0];
        adapter.notifyDataSetChanged();
        ui.postDelayed(new Runnable() {
            int step = 1;
            @Override public void run() {
                if (step < dots.length) {
                    work.text = dots[step++];
                    adapter.notifyDataSetChanged();
                    ui.postDelayed(this, 220);
                } else {
                    reveal(work, answer);
                }
            }
        }, 300);
    }

    /** stream the answer word-by-word — feels alive, costs nothing */
    private void reveal(final Msg work, final String full) {
        curWork = work;
        curFull = full;
        final String[] parts = full.trim().split("(?<=\\s)");
        revealRun = new Runnable() {
            int i = 1;
            @Override public void run() {
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < i && k < parts.length; k++) sb.append(parts[k]);
                work.text = sb.toString();
                adapter.notifyDataSetChanged();
                if (list != null && msgs.size() > 0) {
                    list.setSelection(msgs.size() - 1);
                }
                if (i < parts.length) {
                    i++;
                    ui.postDelayed(this, 26);
                } else {
                    typingAnim = false;
                    sendIcon.setImageResource(R.drawable.ic_send);
                }
            }
        };
        ui.postDelayed(revealRun, 120);
    }

    /** ⏹ stop pressed: finish the reply instantly */
    private void stopReveal() {
        if (revealRun != null) ui.removeCallbacks(revealRun);
        if (curWork != null && curFull != null) curWork.text = curFull;
        typingAnim = false;
        sendIcon.setImageResource(R.drawable.ic_send);
        adapter.notifyDataSetChanged();
    }

    private class ChatAdapter extends BaseAdapter {
        @Override public int getCount() { return msgs.size(); }
        @Override public Object getItem(int position) { return msgs.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) {
            return msgs.get(position).role.equals("user") ? 0 : 1;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            int type = getItemViewType(position);
            if (v == null) {
                v = getLayoutInflater().inflate(type == 0
                        ? R.layout.item_msg_user : R.layout.item_msg_ai, parent, false);
            }
            ((TextView) v.findViewById(R.id.m_text)).setText(msgs.get(position).text);
            if (type == 1) {
                final String txt = msgs.get(position).text;
                v.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View b) {
                        try {
                            android.content.ClipboardManager cm =
                                (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                            cm.setText(txt);
                            android.widget.Toast.makeText(LocalAiActivity.this,
                                    R.string.ai_copied, android.widget.Toast.LENGTH_SHORT).show();
                        } catch (Exception ignored) { }
                        return true;
                    }
                });
            }
            return v;
        }
    }
}
