package io.github.cvhhji.gmsnetguard;

import android.content.*;
import android.os.*;
import java.lang.reflect.Method;
import java.util.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MainHook implements IXposedHookLoadPackage {
 private static final String TAG="GmsNetGuard";
 private static final String PHONE_MANAGER="com.coloros.phonemanager";
 private static final String[] ANTI_FRAUD_COMPONENTS={
  "com.oplus.phonemanager.aivoicecalldetect.antifraudhome.SecurityHomeActivity",
  "com.oplus.phonemanager.aivoicecalldetect.settings.AiVoiceCallDetectSettingsActivity",
  "com.oplus.phonemanager.aivoicecalldetect.riskdetail.ui.AntiFraudSeqDetailsActivity",
  "com.oplus.phonemanager.aivoicecalldetect.antifraudrecords.view.SecurityEventStatisticsActivity",
  "com.oplus.phonemanager.aivoicecalldetect.dialog.FraudRiskDialogActivity",
  "com.oplus.phonemanager.aivoicecalldetect.receiver.InCallRiskDialogReceiver",
  "com.oplus.phonemanager.aivoicecalldetect.trigger.VoipCallDetectTriggerReceiver",
  "com.oplus.phonemanager.aivoicecalldetect.trigger.SimCallDetectTriggerService",
  "com.oplus.phonemanager.aivoicecalldetect.service.AiVoiceDetectForegroundService",
  "com.oplus.phonemanager.aivoicecalldetect.provider.AiVoiceDetectProvider",
  "com.oplus.phonemanager.aivoicecalldetect.provider.FeedbackFileLogProvider",
  "com.oplus.phonemanager.common.provider.FraudDetectRuleFilePipeProvider"
 };
 private static final Set<String> TARGETS=new HashSet<>(Arrays.asList("com.google.android.gms","com.android.vending","com.google.android.gsf"));
 @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
  hookWriter(p.classLoader,"android.net.OplusNetworkingControlManager");
  hookWriter(p.classLoader,"android.net.IOplusNetworkingControlManager$Stub$Proxy");
  if("android".equals(p.packageName)) try {
   XposedHelpers.findAndHookMethod("com.android.server.SystemServer",p.classLoader,"startOtherServices",new XC_MethodHook(){
    @Override protected void afterHookedMethod(MethodHookParam q){ startEventRepair((Context)XposedHelpers.getObjectField(q.thisObject,"mSystemContext")); }
   });
  } catch(Throwable t){ log("SystemServer hook unavailable",t); }
 }
 private static void hookWriter(ClassLoader cl,String name){ try {
  XposedBridge.hookAllMethods(XposedHelpers.findClass(name,cl),"setUidPolicy",new XC_MethodHook(){
   @Override protected void beforeHookedMethod(MethodHookParam p){
    if(p.args.length>=2 && p.args[0] instanceof Integer && p.args[1] instanceof Integer && (Integer)p.args[1]!=0 && isGoogleUid((Integer)p.args[0])){
     p.args[1]=0; XposedBridge.log(TAG+": blocked OEM deny uid="+p.args[0]);
    }
   }
  });
 } catch(Throwable ignored){} }
 private static boolean isGoogleUid(int uid){ try {
  Object pm=Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null);
  String[] ps=(String[])pm.getClass().getMethod("getPackagesForUid",int.class).invoke(pm,uid);
  if(ps!=null) for(String p:ps) if(TARGETS.contains(p)) return true;
 } catch(Throwable t){ log("UID lookup",t); } return false; }
 private static void startEventRepair(Context c){
  Handler h=new Handler(Looper.getMainLooper());
  // Clear any stale OEM policy once after system services are ready.
  h.postDelayed(()->{ repair(c); disableAntiFraud(c); },5000);

  BroadcastReceiver receiver=new BroadcastReceiver(){
   @Override public void onReceive(Context x,Intent i){
    String action=i.getAction();
    if(Intent.ACTION_PACKAGE_ADDED.equals(action)||Intent.ACTION_PACKAGE_REPLACED.equals(action)){
     if(i.getData()==null) return;
     String pkg=i.getData().getSchemeSpecificPart();
     if(PHONE_MANAGER.equals(pkg)){
      h.postDelayed(()->disableAntiFraud(x),1500);
      return;
     }
     if(!TARGETS.contains(pkg)) return;
    }
    // Let ColorOS finish its own policy update before clearing stale state.
    h.removeCallbacksAndMessages(null);
    h.postDelayed(()->repair(x),1500);
   }
  };
  try {
   IntentFilter systemEvents=new IntentFilter();
   systemEvents.addAction(Intent.ACTION_BOOT_COMPLETED);
   systemEvents.addAction("android.net.conn.CONNECTIVITY_CHANGE");
   c.registerReceiver(receiver,systemEvents,Context.RECEIVER_NOT_EXPORTED);

   IntentFilter packageEvents=new IntentFilter();
   packageEvents.addAction(Intent.ACTION_PACKAGE_ADDED);
   packageEvents.addAction(Intent.ACTION_PACKAGE_REPLACED);
   packageEvents.addDataScheme("package");
   c.registerReceiver(receiver,packageEvents,Context.RECEIVER_NOT_EXPORTED);
  } catch(Throwable t){ log("receiver",t); }
 }
 private static void disableAntiFraud(Context c){
  android.content.pm.PackageManager pm=c.getPackageManager();
  for(String cls:ANTI_FRAUD_COMPONENTS) try {
   android.content.ComponentName cn=new android.content.ComponentName(PHONE_MANAGER,cls);
   if(pm.getComponentEnabledSetting(cn)!=android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED){
    pm.setComponentEnabledSetting(cn,android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,android.content.pm.PackageManager.DONT_KILL_APP);
    XposedBridge.log(TAG+": disabled anti-fraud component "+cls);
   }
  } catch(Throwable t){ log("disable anti-fraud "+cls,t); }
 }
 private static void repair(Context c){ try { Object m=c.getSystemService("networking_control"); if(m==null)return; Method set=m.getClass().getMethod("setUidPolicy",int.class,int.class);
  for(String p:TARGETS) try { set.invoke(m,c.getPackageManager().getPackageUid(p,0),0); } catch(Throwable t){ log("repair "+p,t); }
 } catch(Throwable t){ log("manager",t); } }
 private static void log(String s,Throwable t){ XposedBridge.log(TAG+": "+s+": "+t); }
}
