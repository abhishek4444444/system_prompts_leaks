package in.sarkarialert.app;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Creative translator: paste any English notification text and read it in
 * Hindi (or English, or Hinglish). Works on Google's public translate
 * endpoint, and can hand the result straight to the AI assistant.
 */
public class TranslateActivity extends Activity {

    private EditText src;
    private LinearLayout outBox;
    private TextView out, outLabel;
    private String mode = "hi";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_translate);
        src = (EditText) findViewById(R.id.t_src);
        outBox = (LinearLayout) findViewById(R.id.t_out_box);
        out = (TextView) findViewById(R.id.t_out);
        outLabel = (TextView) findViewById(R.id.t_out_label);

        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.t_paste).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    CharSequence t = cm.getText();
                    if (t == null || t.length() == 0) {
                        Toast.makeText(TranslateActivity.this,
                                R.string.translate_clip_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    src.setText(t);
                    src.setSelection(t.length());
                } catch (Exception e) {
                    Toast.makeText(TranslateActivity.this,
                            R.string.translate_clip_empty, Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.t_clear).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                src.setText("");
                outBox.setVisibility(View.GONE);
            }
        });

        pickMode("hi");
        findViewById(R.id.t_mode_hi).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickMode("hi"); }
        });
        findViewById(R.id.t_mode_en).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickMode("en"); }
        });
        findViewById(R.id.t_mode_rm).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickMode("rm"); }
        });

        findViewById(R.id.t_go).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { go(); }
        });
        findViewById(R.id.t_copy).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setText(out.getText());
                    Toast.makeText(TranslateActivity.this,
                            R.string.translate_copied, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) { }
            }
        });
        findViewById(R.id.t_askai).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(TranslateActivity.this, AiChatActivity.class);
                i.putExtra("prefill", getString(R.string.ai_prefill_translate)
                        + "\n\n" + out.getText());
                startActivity(i);
            }
        });
    }

    private void pickMode(String m) {
        mode = m;
        int[] ids = {R.id.t_mode_hi, R.id.t_mode_en, R.id.t_mode_rm};
        for (int id : ids) {
            TextView c = (TextView) findViewById(id);
            boolean sel = (id == R.id.t_mode_hi && m.equals("hi"))
                    || (id == R.id.t_mode_en && m.equals("en"))
                    || (id == R.id.t_mode_rm && m.equals("rm"));
            c.setBackgroundResource(sel ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            c.setTextColor(sel ? 0xFFFFFFFF : 0xFF63707F);
        }
    }

    private void go() {
        final String text = src.getText().toString().trim();
        if (text.length() == 0) {
            Toast.makeText(this, R.string.translate_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (text.length() > 6000) {
            Toast.makeText(this, R.string.translate_too_long, Toast.LENGTH_SHORT).show();
            return;
        }
        Button go = (Button) findViewById(R.id.t_go);
        go.setText(R.string.translating);
        go.setEnabled(false);
        outBox.setVisibility(View.GONE);
        Translate.run(text, mode, new Translate.Cb() {
            @Override public void done(final String result, final String detected, final String err) {
                Button go1 = (Button) findViewById(R.id.t_go);
                go1.setText(R.string.translate_btn);
                go1.setEnabled(true);
                if (err != null) {
                    Toast.makeText(TranslateActivity.this,
                            getString(R.string.translate_fail), Toast.LENGTH_LONG).show();
                    return;
                }
                out.setText(result);
                outLabel.setText(detected == null || detected.length() == 0
                        ? getString(R.string.translate_out)
                        : getString(R.string.translate_detected, detected.toUpperCase()));
                outBox.setVisibility(View.VISIBLE);
            }
        });
    }
}
