package io.shakeguard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.*;

public final class MainActivity extends Activity {
    private Store store;
    private LinearLayout body;
    private final int ink = Color.rgb(24,50,46), green = Color.rgb(8,127,115);
    @Override public void onCreate(Bundle state) { super.onCreate(state); store = new Store(this); }
    @Override public void onResume() { super.onResume(); render(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(ink);
        view.setPadding(0, dp(6), 0, dp(6)); return view;
    }
    private void heading(LinearLayout parent, String value) {
        TextView v = text(value, 20); v.setTypeface(null, Typeface.BOLD); parent.addView(v);
    }
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18),dp(12),dp(18),dp(16));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(20)); card.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2); params.bottomMargin=dp(14); body.addView(card,params); return card;
    }
    private void button(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextColor(green); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); parent.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }
    private boolean serviceEnabled() {
        String value = Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (value == null) return false;
        ComponentName expected = new ComponentName(this, GuardService.class);
        for (String part : value.split(":")) if (expected.equals(ComponentName.unflattenFromString(part))) return true;
        return false;
    }
    private void render() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(243,247,245));
        scroll.setOnApplyWindowInsetsListener((view,insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else view.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            return insets;
        });
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20),dp(18),dp(20),dp(24)); scroll.addView(body);
        TextView title = text("摇跳卫士",32); title.setTypeface(null,Typeface.BOLD); body.addView(title);
        body.addView(text("少一次误跳转，多一分清静",15));
        LinearLayout status = card(); heading(status,"免 Root · 本地防护");
        status.addView(text("无障碍服务：" + (GuardService.connected?"已连接":serviceEnabled()?"系统已启用，但当前未连接":"未启用"),16));
        Switch toggle = new Switch(this); toggle.setText("启用规则检测"); toggle.setChecked(store.enabled());
        toggle.setOnCheckedChangeListener((v,on) -> { store.enabled(on); render(); }); status.addView(toggle);
        long pause = store.pauseRemaining();
        if (pause>0) status.addView(text("已暂停，约 " + ((pause+59999)/60000)+" 分钟后恢复",15));
        button(status,"设置无障碍服务",this::disclosure);
        button(status,pause>0?"立即恢复":"暂停 5 分钟",() -> {store.pause(pause==0); render();});
        LinearLayout info = card(); heading(info,"先了解，再开启");
        info.addView(text("本软件在可疑跨应用跳转后尝试返回，无法阻止目标应用启动。没有点击事件不代表一定是广告，可能误判登录、支付或分享。建议先用仅记录模式。",15));
        LinearLayout rules = card(); heading(rules,"跳转规则");
        List<RuleEngine.Rule> list = store.rules();
        if (list.isEmpty()) rules.addView(text("还没有规则。添加广告所在应用和跳转目标应用，默认仅记录。",15));
        for (int i=0;i<list.size();i++) {
            final int index=i; RuleEngine.Rule r=list.get(i);
            rules.addView(text(r.source+"\n↓\n"+r.target+"\n前 "+r.seconds+" 秒 · "+(r.automatic?"自动返回":"仅记录"),15));
            button(rules,"编辑 / 删除此规则",()->editRule(index));
        }
        button(rules,"＋ 添加规则",()->editRule(-1));
        LinearLayout logs=card(); heading(logs,"本地记录");
        logs.addView(text("只保存包名、时间和结果，最多 200 条，保留 7 天。不读取屏幕正文，不申请联网权限。",14));
        button(logs,"查看记录",this::showLogs);
        button(logs,store.diagnosing()?"停止诊断":"记录 60 秒诊断",()->{
            if(store.diagnosing()){store.stopDiagnostics();render();return;}
            new AlertDialog.Builder(this).setTitle("记录一次跳转过程")
                .setMessage("接下来 60 秒在本地记录窗口包名、事件类型与未匹配原因，最多与普通日志共保留 200 条。不会读取屏幕文字或联网。请开启后回桌面，重新打开微博并复现，再回来查看记录。")
                .setPositiveButton("开始",(d,w)->{store.startDiagnostics();render();}).setNegativeButton("取消",null).show();
        });
        button(logs,"清空记录",()->new AlertDialog.Builder(this).setMessage("清空所有本地记录？").setPositiveButton("清空",(d,w)->{store.clearLogs();render();}).setNegativeButton("取消",null).show());
        body.addView(text("v0.1.1 · 实验版本\n遇到系统询问是否打开其他应用时，请先取消。经过确认框的跳转可能无法匹配，使用 60 秒诊断查看具体包名路径。",13));
        setContentView(scroll); scroll.requestApplyInsets();
    }
    private void disclosure() {
        new AlertDialog.Builder(this).setTitle("无障碍服务使用说明")
            .setMessage("开启后，摇跳卫士会观察其他应用的窗口包名、点击事件及时间，用于匹配您设置的来源 → 目标规则。\n\n启用自动返回的规则会代您执行一次系统返回，可能误判主动跳转。我们不读取节点正文、不截屏、不上传数据。您可以随时关闭规则或在系统中停用服务。\n\nAndroid 对侧载应用可能显示“受限设置”。请只在确认安装来源可靠后自行按系统提示处理。")
            .setPositiveButton("了解并前往系统设置",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .setNegativeButton("取消",null).show();
    }
    private EditText field(LinearLayout parent,String hint,String value,boolean number) {
        EditText f=new EditText(this);f.setHint(hint);f.setText(value);f.setSingleLine(true);
        f.setInputType(number?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);parent.addView(f);return f;
    }
    private void editRule(int index) {
        List<RuleEngine.Rule> rules=store.rules(); RuleEngine.Rule existing=index<0?null:rules.get(index);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(22),dp(8),dp(22),dp(8));
        EditText source=field(form,"来源包名，例如 com.example.news",existing==null?"":existing.source,false);
        button(form,"选择来源应用",()->pickApp(source));
        EditText target=field(form,"目标包名，例如 com.example.shop",existing==null?"":existing.target,false);
        button(form,"选择目标应用",()->pickApp(target));
        EditText seconds=field(form,"启动保护秒数：3～15",existing==null?"6":String.valueOf(existing.seconds),true);
        CheckBox automatic=new CheckBox(this);automatic.setText("自动返回（可能误判，默认仅记录）");automatic.setChecked(existing!=null&&existing.automatic);form.addView(automatic);
        form.addView(text("只针对这对应用；不会准确识别摇动。看不到的应用可手动填写包名。",13));
        ScrollView scroller=new ScrollView(this);scroller.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(index<0?"添加规则":"编辑规则").setView(scroller).setPositiveButton("保存",null).setNegativeButton("取消",null).setNeutralButton(index<0?"":"删除",(d,w)->{if(index>=0){rules.remove(index);store.rules(rules);render();}}).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String a=source.getText().toString().trim(), b=target.getText().toString().trim();int sec;
            try {sec=Integer.parseInt(seconds.getText().toString());}catch(NumberFormatException e){seconds.setError("请输入 3～15");return;}
            if(!validPackage(a)){source.setError("请输入有效包名");return;}
            if(!validPackage(b)||a.equals(b)){target.setError("目标必须为另一个应用的包名");return;}
            if(a.equals(getPackageName())||b.equals(getPackageName())||a.equals("android")||b.equals("android")||a.startsWith("com.android.")||b.startsWith("com.android.")){target.setError("请勿对本应用或 Android 系统组件配置规则");return;}
            if(sec<3||sec>15){seconds.setError("范围为 3～15 秒");return;}
            for(int i=0;i<rules.size();i++)if(i!=index&&rules.get(i).source.equals(a)&&rules.get(i).target.equals(b)){target.setError("此规则已存在");return;}
            Runnable save=()->{RuleEngine.Rule r=new RuleEngine.Rule(a,b,sec,automatic.isChecked());if(index<0)rules.add(r);else rules.set(index,r);store.rules(rules);dialog.dismiss();render();};
            if(automatic.isChecked()&&(existing==null||!existing.automatic||!existing.source.equals(a)||!existing.target.equals(b)))new AlertDialog.Builder(this).setTitle("启用自动返回？").setMessage("此规则可能将主动登录、支付或分享误认为广告。匹配后会代您按一次返回。请确认您已经通过仅记录模式验证。") .setPositiveButton("启用此规则",(x,w)->save.run()).setNegativeButton("取消",null).show();else save.run();
        }));dialog.show();
    }
    private boolean validPackage(String p){return p.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");}
    @SuppressWarnings("deprecation")
    private void pickApp(EditText destination){
        Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found=getPackageManager().queryIntentActivities(launcher,0);
        Map<String,String> names=new HashMap<>();
        for(ResolveInfo r:found)if(!r.activityInfo.packageName.equals(getPackageName()))names.put(r.activityInfo.packageName,r.loadLabel(getPackageManager()).toString());
        List<String> packages=new ArrayList<>(names.keySet());packages.sort(Comparator.comparing(p->names.get(p),String.CASE_INSENSITIVE_ORDER));
        String[] labels=new String[packages.size()];for(int i=0;i<labels.length;i++)labels[i]=names.get(packages.get(i))+"\n"+packages.get(i);
        if(labels.length==0){new AlertDialog.Builder(this).setMessage("系统未返回可见应用，请手动输入包名。").setPositiveButton("知道了",null).show();return;}
        new AlertDialog.Builder(this).setTitle("选择可见应用").setItems(labels,(d,w)->destination.setText(packages.get(w))).setNegativeButton("取消",null).show();
    }
    private void showLogs(){
        JSONArray logs=store.logs();StringBuilder value=new StringBuilder();DateFormat format=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM);
        for(int i=0;i<logs.length();i++){JSONObject r=logs.optJSONObject(i);if(r!=null)value.append(format.format(new Date(r.optLong("time")))).append('\n').append(r.optString("source")).append(" → ").append(r.optString("target")).append('\n').append(r.optString("result")).append("\n\n");}
        TextView content=text(value.length()==0?"暂无记录。保护开关和无障碍服务都开启后，匹配规则才会记录。":value.toString(),14);content.setPadding(dp(22),dp(10),dp(22),dp(10));content.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);new AlertDialog.Builder(this).setTitle("最近 7 天 · 最多 200 条").setView(scroll).setPositiveButton("关闭",null).show();
    }
}
