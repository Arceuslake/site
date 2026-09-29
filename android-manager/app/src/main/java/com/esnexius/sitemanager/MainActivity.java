package com.esnexius.sitemanager;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    public static final String ACTION_COMMAND_RESULT = "com.esnexius.sitemanager.COMMAND_RESULT";
    private static final int REQ_TERMUX = 1001, REQ_ZIP = 1002, REQ_STORAGE = 1003;
    private final Map<String,Button> buttons = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView termux, website, state, log;
    private boolean registered;

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { handleResult(i); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        refreshLocalTermux();
        if (TermuxBridge.isTermuxInstalled(this) && !TermuxBridge.hasRunCommandPermission(this))
            TermuxBridge.requestRunCommandPermission(this, REQ_TERMUX);
    }

    @Override protected void onStart() {
        super.onStart();
        if (!registered) {
            IntentFilter f = new IntentFilter(ACTION_COMMAND_RESULT);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(resultReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(resultReceiver, f);
            registered = true;
        }
        refreshLocalTermux();
    }

    @Override protected void onStop() {
        if (registered) { unregisterReceiver(resultReceiver); registered = false; }
        super.onStop();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = column();
        root.setPadding(dp(18),dp(20),dp(18),dp(28));
        root.setBackgroundColor(Color.rgb(244,248,251));
        scroll.addView(root);

        LinearLayout hero = column();
        hero.setPadding(dp(18),dp(18),dp(18),dp(18));
        hero.setBackground(bg("#101820",22));
        root.addView(hero, full());

        TextView title = label("Esnexius Site Manager",25,Color.WHITE,true);
        hero.addView(title);
        TextView sub = label("Termux controller for your storefront",14,Color.rgb(190,220,238),false);
        hero.addView(sub);

        LinearLayout statusRow = row();
        LinearLayout.LayoutParams srp = full(); srp.topMargin=dp(14);
        hero.addView(statusRow,srp);
        termux = pill("Termux: checking");
        website = pill("Website: unknown");
        statusRow.addView(termux, weight());
        statusRow.addView(website, weight());

        root.addView(label("Website controls",18,Color.rgb(16,24,32),true), spaced(16,8));
        root.addView(label("Upload keeps your selected website ZIP in Downloads. Update re-deploys that ZIP after a safety backup and preserves .env and data.",13,Color.DKGRAY,false), spaced(0,12));

        addPair(root, action("install","INSTALL", Operations.install()), action("upload","UPLOAD ZIP", null));
        addPair(root, action("update","UPDATE", Operations.update()), action("backup","BACKUP", Operations.backup()));
        addPair(root, action("start","START", Operations.start()), action("stop","STOP", Operations.stop()));
        addPair(root, action("status","REFRESH STATUS", Operations.status()), action("logs","VIEW LOG", Operations.logs()));
        addPair(root, utility("OPEN WEBSITE", v -> openWebsite()), utility("TERMUX SETUP", v -> setupTermux()));

        state = label("Ready",14,Color.rgb(25,75,105),true);
        state.setPadding(dp(14),dp(12),dp(14),dp(12));
        state.setBackground(bg("#E4F2FC",14));
        root.addView(state, spaced(4,10));

        log = label("Tap TERMUX SETUP once, paste the copied command into Termux, approve storage access, then return here.",12,Color.rgb(220,235,244),false);
        log.setTypeface(android.graphics.Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        log.setPadding(dp(14),dp(14),dp(14),dp(14));
        log.setBackground(bg("#17232C",15));
        root.addView(log,new LinearLayout.LayoutParams(-1,dp(240)));

        root.addView(label("Install path: ~/esnexiusstore-phone-shop\nUpload: Downloads/EsnexiusManager/upload.zip",11,Color.GRAY,false), spaced(10,0));
        return scroll;
    }

    private Button action(String key, String text, String script) {
        Button b = base(text);
        buttons.put(key,b);
        if ("upload".equals(key)) b.setOnClickListener(v -> beginUpload());
        else b.setOnClickListener(v -> run(key, script));
        return b;
    }

    private Button utility(String text, View.OnClickListener l) {
        Button b = base(text); b.setOnClickListener(l); return b;
    }

    private Button base(String text) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(12); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        b.setBackground(bg("#246B9A",14));
        return b;
    }

    private void addPair(LinearLayout root, Button a, Button b) {
        LinearLayout r = row();
        LinearLayout.LayoutParams rp = full(); rp.bottomMargin=dp(9); root.addView(r,rp);
        LinearLayout.LayoutParams p1=weight(); p1.rightMargin=dp(5);
        LinearLayout.LayoutParams p2=weight(); p2.leftMargin=dp(5);
        r.addView(a,p1); r.addView(b,p2);
    }

    private void run(String op, String script) {
        Button b=buttons.get(op); if (b!=null) b.setEnabled(false);
        state.setText(pretty(op)+" — Running");
        log.setText("Sending command to Termux…");
        try { TermuxBridge.runBash(this,op,script); }
        catch (SecurityException e) {
            if (b!=null) b.setEnabled(true);
            state.setText(pretty(op)+" — Permission required");
            log.setText("Grant Run commands in Termux environment, then retry.");
            TermuxBridge.requestRunCommandPermission(this,REQ_TERMUX);
        } catch (Exception e) {
            if (b!=null) b.setEnabled(true);
            state.setText(pretty(op)+" — Failed");
            log.setText(String.valueOf(e.getMessage()));
        }
    }

    private void handleResult(Intent i) {
        String op=i.getStringExtra("operation");
        Button b=buttons.get(op); if (b!=null) b.setEnabled(true);
        int exit=i.getIntExtra("exit_code",-999), err=i.getIntExtra("err",0);
        String out=safe(i.getStringExtra("stdout")), stderr=safe(i.getStringExtra("stderr")), msg=safe(i.getStringExtra("errmsg"));
        boolean ok=exit==0 && err==0;
        state.setText(pretty(op)+(ok?" — Success":" — Failed ("+exit+")"));
        String all=out;
        if(!stderr.isBlank()) all += (all.isBlank()?"":"\n\n")+"stderr:\n"+stderr;
        if(!msg.isBlank()) all += (all.isBlank()?"":"\n\n")+"Termux:\n"+msg;
        log.setText(all.isBlank()?"No command output.":all.trim());
        if(ok) setPill(termux,"Termux: connected","#2D6A4F");
        if("status".equals(op)) applyStatus(out);
        else if(ok && !"logs".equals(op)) handler.postDelayed(() -> run("status",Operations.status()),700);
    }

    private void applyStatus(String out) {
        if(out.contains("HTTP=online")) setPill(website,"Website: online","#2D6A4F");
        else if(out.contains("SERVER=running")) setPill(website,"Website: starting","#A36A18");
        else if(out.contains("INSTALLED=yes")) setPill(website,"Website: stopped","#6B7280");
        else setPill(website,"Website: not installed","#9B3D3D");
    }

    private void beginUpload() {
        if (Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        chooseZip();
    }

    private void chooseZip() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});
        startActivityForResult(i,REQ_ZIP);
    }

    @Override protected void onActivityResult(int req,int result,Intent data) {
        super.onActivityResult(req,result,data);
        if(req==REQ_ZIP && result==RESULT_OK && data!=null && data.getData()!=null) {
            try {
                saveZip(data.getData());
                log.setText("ZIP copied to Downloads/EsnexiusManager/upload.zip");
                run("upload",Operations.upload());
            } catch(Exception e) {
                state.setText("Upload — Copy failed"); log.setText(String.valueOf(e.getMessage()));
            }
        }
    }

    private void saveZip(Uri source) throws Exception {
        if (Build.VERSION.SDK_INT <= 28) {
            saveZipLegacy(source);
        } else {
            saveZipScoped(source);
        }
    }

    private void saveZipLegacy(Uri source) throws Exception {
        File downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(downloads, "EsnexiusManager");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create Downloads/EsnexiusManager");
        File dst = new File(dir, "upload.zip");
        try(InputStream in=getContentResolver().openInputStream(source); OutputStream out=new FileOutputStream(dst,false)) {
            if(in==null) throw new IOException("Cannot open selected ZIP");
            copy(in,out);
        }
    }

    private void saveZipScoped(Uri source) throws Exception {
        ContentResolver r=getContentResolver();
        Uri collection=MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String rel="Download/EsnexiusManager/", name="upload.zip";
        String sel=MediaStore.MediaColumns.DISPLAY_NAME+"=? AND "+MediaStore.MediaColumns.RELATIVE_PATH+"=?";
        try(Cursor c=r.query(collection,new String[]{MediaStore.MediaColumns._ID},sel,new String[]{name,rel},null)) {
            while(c!=null && c.moveToNext()) r.delete(ContentUris.withAppendedId(collection,c.getLong(0)),null,null);
        }
        ContentValues v=new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME,name); v.put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH,rel); v.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri dst=r.insert(collection,v); if(dst==null) throw new IOException("Cannot create upload.zip");
        try(InputStream in=r.openInputStream(source); OutputStream out=r.openOutputStream(dst,"w")) {
            if(in==null||out==null) throw new IOException("Cannot open ZIP");
            copy(in,out);
        }
        ContentValues done=new ContentValues(); done.put(MediaStore.MediaColumns.IS_PENDING,0); r.update(dst,done,null,null);
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf=new byte[65536]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n);
    }

    private void setupTermux() {
        String cmd=Operations.termuxSetupCommand();
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Termux setup",cmd));
        log.setText("Copied setup command:\n\n"+cmd+"\n\nPaste it in Termux and press Enter.");
        Intent i=getPackageManager().getLaunchIntentForPackage(TermuxBridge.TERMUX_PACKAGE);
        if(i!=null) startActivity(i); else Toast.makeText(this,"Termux is not installed.",Toast.LENGTH_LONG).show();
    }

    private void openWebsite() {
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("http://127.0.0.1:3000"))); }
        catch(Exception e) { Toast.makeText(this,"No browser available.",Toast.LENGTH_LONG).show(); }
    }

    private void refreshLocalTermux() {
        if(!TermuxBridge.isTermuxInstalled(this)) setPill(termux,"Termux: not installed","#9B3D3D");
        else if(!TermuxBridge.hasRunCommandPermission(this)) setPill(termux,"Termux: permission needed","#A36A18");
        else setPill(termux,"Termux: ready","#2D6A4F");
    }

    @Override public void onRequestPermissionsResult(int req,String[] p,int[] g) {
        super.onRequestPermissionsResult(req,p,g);
        if(req==REQ_TERMUX) refreshLocalTermux();
        if(req==REQ_STORAGE && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) chooseZip();
    }

    private LinearLayout column(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView label(String s,int sp,int color,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD); return t; }
    private TextView pill(String s){ TextView t=label(s,12,Color.WHITE,true); t.setGravity(Gravity.CENTER); t.setPadding(dp(6),0,dp(6),0); t.setBackground(bg("#3A4A56",12)); return t; }
    private void setPill(TextView t,String s,String color){ t.setText(s); t.setBackground(bg(color,12)); }
    private GradientDrawable bg(String c,int r){ GradientDrawable g=new GradientDrawable(); g.setColor(Color.parseColor(c)); g.setCornerRadius(dp(r)); return g; }
    private LinearLayout.LayoutParams full(){ return new LinearLayout.LayoutParams(-1,-2); }
    private LinearLayout.LayoutParams weight(){ return new LinearLayout.LayoutParams(0,dp(54),1f); }
    private LinearLayout.LayoutParams spaced(int top,int bottom){ LinearLayout.LayoutParams p=full(); p.topMargin=dp(top); p.bottomMargin=dp(bottom); return p; }
    private int dp(int x){ return Math.round(x*getResources().getDisplayMetrics().density); }
    private static String safe(String s){ return s==null?"":s; }
    private static String pretty(String s){ return s==null||s.isBlank()?"Command":Character.toUpperCase(s.charAt(0))+s.substring(1); }
}
