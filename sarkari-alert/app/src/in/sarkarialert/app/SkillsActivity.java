package in.sarkarialert.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Skills — user-installed AI personas/instructions (e.g. a full skill.md
 * prompt). Skills are used in TWO places:
 *  • Cloud AI (Gemini/Groq/DeepSeek): injected into the system prompt, so
 *    the model answers in that style/format.
 *  • Sarkari Brain (local): recalls and shows the skill content when a
 *    question matches the skill's name.
 */
public class SkillsActivity extends Activity {

    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_skills);
        box = (LinearLayout) findViewById(R.id.skills_container);
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.skills_add).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { edit(-1, null, null); }
        });
        findViewById(R.id.skills_add_file).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickFile(); }
        });
        render();
    }

    private void pickFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "text/plain", "text/markdown", "application/json",
                    "application/octet-stream", "text/*"});
            startActivityForResult(i, 41);
        } catch (Exception e) {
            Toast.makeText(this, R.string.skills_file_fail, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 41 || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        try {
            java.io.InputStream in = getContentResolver().openInputStream(data.getData());
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 200 * 1024) {
                bo.write(buf, 0, n);
                total += n;
            }
            in.close();
            String content = bo.toString("UTF-8").trim();
            if (content.length() == 0) {
                Toast.makeText(this, R.string.skills_file_fail, Toast.LENGTH_SHORT).show();
                return;
            }
            String name = "";
            android.database.Cursor c = getContentResolver().query(data.getData(), null, null, null, null);
            if (c != null) {
                try {
                    int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0 && c.moveToFirst()) name = c.getString(idx);
                } finally {
                    c.close();
                }
            }
            if (name == null || name.length() == 0) {
                String last = data.getData().getLastPathSegment();
                name = last == null ? "" : last.substring(last.lastIndexOf('/') + 1);
            }
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            edit(-1, name.trim(), content);
        } catch (Exception e) {
            Toast.makeText(this, R.string.skills_file_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void render() {
        box.removeAllViews();
        JSONArray arr = Prefs.skills(this);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        if (arr.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.skills_empty));
            empty.setTextSize(13);
            empty.setTextColor(0xFF63707F);
            empty.setPadding(pad, pad * 2, pad, pad);
            box.addView(empty);
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            box.addView(row(o, i));
        }
    }

    private View row(final JSONObject o, final int index) {
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        row.setLayoutParams(lp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(clp);

        TextView name = new TextView(this);
        name.setText(o.optString("name", "Skill"));
        name.setTextSize(15);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setTextColor(0xFF1B2430);
        col.addView(name);

        String content = o.optString("content", "");
        TextView preview = new TextView(this);
        preview.setText(content.replace('\n', ' '));
        preview.setTextSize(11);
        preview.setTextColor(0xFF63707F);
        preview.setMaxLines(1);
        col.addView(preview);

        TextView meta = new TextView(this);
        meta.setText(o.optBoolean("enabled", true)
                ? R.string.skills_used_by : R.string.skills_disabled);
        meta.setTextSize(10);
        meta.setTextColor(0xFFD95F14);
        col.addView(meta);

        row.addView(col);

        final SkillsActivity self = this;
        Switch sw = new Switch(this);
        sw.setChecked(o.optBoolean("enabled", true));
        sw.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    JSONArray arr = Prefs.skills(self);
                    if (index < arr.length()) {
                        arr.getJSONObject(index).put("enabled", ((Switch) v).isChecked());
                        Prefs.setSkills(self, arr);
                    }
                } catch (Exception ignored) { }
            }
        });
        row.addView(sw);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { edit(index); }
        });
        return row;
    }

    private void edit(final int index) { edit(index, null, null); }

    private void edit(final int index, String preName, String preContent) {
        JSONArray arr = Prefs.skills(this);
        JSONObject obj = index >= 0 ? arr.optJSONObject(index) : new JSONObject();

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(pad, pad / 2, pad, 0);

        final EditText name = new EditText(this);
        name.setHint(R.string.skills_name_hint);
        name.setText(index >= 0 || preName == null ? obj.optString("name") : preName);
        name.setMaxLines(1);
        ll.addView(name);

        final EditText content = new EditText(this);
        content.setHint(R.string.skills_content_hint);
        content.setTextSize(13);
        content.setMinLines(6);
        content.setGravity(android.view.Gravity.TOP);
        content.setText(index >= 0 || preContent == null ? obj.optString("content") : preContent);
        ll.addView(content);

        AlertDialog.Builder ab = new AlertDialog.Builder(this)
                .setTitle(index < 0 ? R.string.skills_add : R.string.skills_edit)
                .setView(ll)
                .setPositiveButton(R.string.skills_save, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = name.getText().toString().trim();
                        String ct = content.getText().toString().trim();
                        if (n.length() == 0 || ct.length() == 0) {
                            Toast.makeText(SkillsActivity.this,
                                    R.string.skills_need_both, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            JSONArray arr2 = Prefs.skills(SkillsActivity.this);
                            JSONObject o = new JSONObject();
                            o.put("name", n);
                            o.put("content", ct.length() > 8000 ? ct.substring(0, 8000) : ct);
                            o.put("enabled", true);
                            if (index >= 0) arr2.put(index, o); else arr2.put(o);
                            Prefs.setSkills(SkillsActivity.this, arr2);
                            Toast.makeText(SkillsActivity.this,
                                    R.string.skills_saved, Toast.LENGTH_SHORT).show();
                            render();
                        } catch (Exception ignored) { }
                    }
                })
                .setNegativeButton(R.string.cancel, null);
        if (index >= 0) {
            ab.setNeutralButton(R.string.remove, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    try {
                        JSONArray arr2 = Prefs.skills(SkillsActivity.this);
                        JSONArray out = new JSONArray();
                        for (int i = 0; i < arr2.length(); i++) if (i != index) out.put(arr2.get(i));
                        Prefs.setSkills(SkillsActivity.this, out);
                        Toast.makeText(SkillsActivity.this,
                                R.string.skills_deleted, Toast.LENGTH_SHORT).show();
                        render();
                    } catch (Exception ignored) { }
                }
            });
        }
        ab.show();
    }
}
