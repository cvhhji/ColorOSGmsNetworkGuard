package io.github.cvhhji.gmsnetguard;

import android.content.*;
import android.os.*;
import java.lang.reflect.Method;
import java.util.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MainHook implements IXposedHookLoadPackage {
 private static final String TAG="GmsNetGuard";
 private static final Set<String> TARGETS=new HashSet<>(Arrays.asList("com.google.android.gms","com.android.vending","com.google.android.gsf"));
 @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
  hookWriter(p.classLoader,"android.net.OplusNetworkingControlManager");
  hookWriter(p.classLoader,"android.net.IOplusNetworkingControlManager$Stub$Proxy");
  if("android".equals(p.packageName)) try {
   XposedHelpers.findAndHookMethod("com.android.server.SystemServer",p.classLoader,"startOtherServices",new XC_MethodHook(){
    @Override protected void afterHookedMethod(MethodHookParam q){ startLoop((Context)XposedHelpers.getObjectField(q.thisObject,"mSystemContext")); }
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
 private static void startLoop(Context c){ Handler h=new Handler(Looper.getMainLooper()); Runnable r=new Runnable(){ public void run(){ repair(c); h.postDelayed(this,15000); }}; h.postDelayed(r,5000);
  IntentFilter f=new IntentFilter(); f.addAction(Intent.ACTION_BOOT_COMPLETED); f.addAction("android.net.conn.CONNECTIVITY_CHANGE");
  try { c.registerReceiver(new BroadcastReceiver(){ public void onReceive(Context x,Intent i){ h.postDelayed(()->repair(x),1500); }},f,Context.RECEIVER_NOT_EXPORTED); } catch(Throwable t){ log("receiver",t); }
 }
 private static void repair(Context c){ try { Object m=c.getSystemService("networking_control"); if(m==null)return; Method set=m.getClass().getMethod("setUidPolicy",int.class,int.class);
  for(String p:TARGETS) try { set.invoke(m,c.getPackageManager().getPackageUid(p,0),0); } catch(Throwable t){ log("repair "+p,t); }
 } catch(Throwable t){ log("manager",t); } }
 private static void log(String s,Throwable t){ XposedBridge.log(TAG+": "+s+": "+t); }
}
