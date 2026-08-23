package io.github.cvhhji.gmsnetguard;

import android.content.*;
import android.os.*;
import java.lang.reflect.Method;
import java.util.*;
import dalvik.system.DexFile;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MainHook implements IXposedHookLoadPackage {
 private static final String TAG="GmsAntiFraudGuard", ANDROID="android", PHONE_MANAGER="com.coloros.phonemanager", PHONE="com.android.phone", TELECOM="com.android.server.telecom", NATIONAL_ANTI_FRAUD="com.hicorenational.antifraud";
 // Static declaration; keep synchronized with META-INF/xposed/scope.list.
 private static final Set<String> HOOK_SCOPE=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(ANDROID,PHONE_MANAGER,PHONE,TELECOM,NATIONAL_ANTI_FRAUD)));
 private static final Set<String> GOOGLE_TARGETS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("com.google.android.gms","com.android.vending","com.google.android.gsf")));
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
  "com.oplus.phonemanager.common.provider.FraudDetectRuleFilePipeProvider"};
 private static final String[] FRAUD_TOKENS={"antifraud","anti_fraud","anti fraud","fraud","deepfake","诈骗","反诈"};

 @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p){
  if(!HOOK_SCOPE.contains(p.packageName))return;
  if(ANDROID.equals(p.packageName)){hookWriter(p.classLoader,"android.net.OplusNetworkingControlManager");hookWriter(p.classLoader,"android.net.IOplusNetworkingControlManager$Stub$Proxy");hookSystemServer(p.classLoader);}
  if(PHONE_MANAGER.equals(p.packageName)||PHONE.equals(p.packageName)||TELECOM.equals(p.packageName))neutralizeFraudCode(p.classLoader,p.packageName,p.appInfo);
  if(NATIONAL_ANTI_FRAUD.equals(p.packageName))blockDedicatedAntiFraudApp(p.classLoader);
 }
 private static void hookSystemServer(ClassLoader cl){try{XposedHelpers.findAndHookMethod("com.android.server.SystemServer",cl,"startOtherServices",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam q){startEventRepair((Context)XposedHelpers.getObjectField(q.thisObject,"mSystemContext"));}});}catch(Throwable t){log("SystemServer hook",t);}}
 private static void hookWriter(ClassLoader cl,String name){try{XposedBridge.hookAllMethods(XposedHelpers.findClass(name,cl),"setUidPolicy",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){if(p.args.length>=2&&p.args[0] instanceof Integer&&p.args[1] instanceof Integer&&(Integer)p.args[1]!=0&&isGoogleUid((Integer)p.args[0])){p.args[1]=0;XposedBridge.log(TAG+": blocked OEM deny uid="+p.args[0]);}}});}catch(Throwable ignored){}}

 private static void neutralizeFraudCode(ClassLoader cl,String pkg,android.content.pm.ApplicationInfo ai){
  Set<String> names=new LinkedHashSet<>();
  if(ai!=null){scanDex(ai.sourceDir,names);if(ai.splitSourceDirs!=null)for(String path:ai.splitSourceDirs)scanDex(path,names);}
  for(String cn:names)if(looksFraud(cn))try{neutralizeClass(Class.forName(cn,false,cl),pkg);}catch(Throwable ignored){}
  // Device-version-specific ColorOS Telecom path that disconnects calls classified as National Anti-Fraud Center calls.
  if(TELECOM.equals(pkg))try{
   Class<?> call=Class.forName("com.android.server.telecom.Call",false,cl);
   XposedHelpers.findAndHookMethod("com.android.server.telecom.j6",cl,"E",call,new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){p.setResult(null);}});
   XposedHelpers.findAndHookMethod("com.android.server.telecom.number.j",cl,"c",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){p.setResult(false);}});
  }catch(Throwable t){log("Telecom anti-fraud path",t);}
 }
 private static void scanDex(String path,Set<String> out){if(path==null)return;try{DexFile d=new DexFile(path);Enumeration<String> e=d.entries();while(e.hasMoreElements()){String n=e.nextElement();if(looksFraud(n))out.add(n);}d.close();}catch(Throwable t){log("scan "+path,t);}}
 private static void neutralizeClass(Class<?> c,String pkg){try{int count=0;for(final Method m:c.getDeclaredMethods()){final Object value=neutralValue(m.getReturnType());XposedBridge.hookMethod(m,new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){p.setResult(value);}});count++;}if(count>0)XposedBridge.log(TAG+": neutralized "+count+" methods in "+pkg+"/"+c.getName());}catch(Throwable t){log("neutralize "+c.getName(),t);}}
 private static boolean looksFraud(String v){String s=v.toLowerCase(Locale.ROOT);for(String t:FRAUD_TOKENS)if(s.contains(t))return true;return false;}
 private static Object neutralValue(Class<?> t){if(t==Void.TYPE)return null;if(t==Boolean.TYPE)return false;if(t==Byte.TYPE)return(byte)0;if(t==Short.TYPE)return(short)0;if(t==Integer.TYPE)return 0;if(t==Long.TYPE)return 0L;if(t==Float.TYPE)return 0f;if(t==Double.TYPE)return 0d;if(t==Character.TYPE)return'\0';return null;}
 private static void blockDedicatedAntiFraudApp(ClassLoader cl){try{XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.Application",cl),"onCreate",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){android.app.Application a=(android.app.Application)p.thisObject;if(NATIONAL_ANTI_FRAUD.equals(a.getPackageName())){a.getPackageManager().setApplicationEnabledSetting(NATIONAL_ANTI_FRAUD,android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,0);Process.killProcess(Process.myPid());}}});}catch(Throwable t){log("dedicated anti-fraud block",t);}}
 private static boolean isGoogleUid(int uid){try{Object pm=Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null);String[] ps=(String[])pm.getClass().getMethod("getPackagesForUid",int.class).invoke(pm,uid);if(ps!=null)for(String p:ps)if(GOOGLE_TARGETS.contains(p))return true;}catch(Throwable t){log("UID lookup",t);}return false;}

 private static void startEventRepair(Context c){Handler h=new Handler(Looper.getMainLooper());h.postDelayed(()->{repair(c);disableAntiFraud(c);},5000);BroadcastReceiver r=new BroadcastReceiver(){@Override public void onReceive(Context x,Intent i){if((Intent.ACTION_PACKAGE_ADDED.equals(i.getAction())||Intent.ACTION_PACKAGE_REPLACED.equals(i.getAction()))&&i.getData()!=null){String pkg=i.getData().getSchemeSpecificPart();if(PHONE_MANAGER.equals(pkg)){h.postDelayed(()->disableAntiFraud(x),1500);return;}if(NATIONAL_ANTI_FRAUD.equals(pkg)){h.postDelayed(()->disablePackage(x,NATIONAL_ANTI_FRAUD),1500);return;}if(!GOOGLE_TARGETS.contains(pkg))return;}h.removeCallbacksAndMessages(null);h.postDelayed(()->repair(x),1500);}};try{IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_BOOT_COMPLETED);f.addAction("android.net.conn.CONNECTIVITY_CHANGE");c.registerReceiver(r,f,Context.RECEIVER_NOT_EXPORTED);IntentFilter p=new IntentFilter();p.addAction(Intent.ACTION_PACKAGE_ADDED);p.addAction(Intent.ACTION_PACKAGE_REPLACED);p.addDataScheme("package");c.registerReceiver(r,p,Context.RECEIVER_NOT_EXPORTED);}catch(Throwable t){log("receiver",t);}}
 private static void disablePackage(Context c,String pkg){try{c.getPackageManager().setApplicationEnabledSetting(pkg,android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,0);XposedBridge.log(TAG+": disabled "+pkg);}catch(Throwable t){log("disable "+pkg,t);}}
 private static void disableAntiFraud(Context c){android.content.pm.PackageManager pm=c.getPackageManager();for(String cls:ANTI_FRAUD_COMPONENTS)try{android.content.ComponentName cn=new android.content.ComponentName(PHONE_MANAGER,cls);if(pm.getComponentEnabledSetting(cn)!=android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED){pm.setComponentEnabledSetting(cn,android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,android.content.pm.PackageManager.DONT_KILL_APP);XposedBridge.log(TAG+": disabled anti-fraud component "+cls);}}catch(Throwable t){log("disable anti-fraud "+cls,t);}try{pm.getApplicationInfo(NATIONAL_ANTI_FRAUD,0);disablePackage(c,NATIONAL_ANTI_FRAUD);}catch(Throwable ignored){}}
 private static void repair(Context c){try{Object m=c.getSystemService("networking_control");if(m==null)return;Method set=m.getClass().getMethod("setUidPolicy",int.class,int.class);for(String p:GOOGLE_TARGETS)try{set.invoke(m,c.getPackageManager().getPackageUid(p,0),0);}catch(Throwable t){log("repair "+p,t);}}catch(Throwable t){log("manager",t);}}
 private static void log(String s,Throwable t){XposedBridge.log(TAG+": "+s+": "+t);}
}
