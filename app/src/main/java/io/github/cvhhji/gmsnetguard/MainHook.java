package io.github.cvhhji.gmsnetguard;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.util.Log;
import java.lang.reflect.*;
import java.util.*;
import dalvik.system.DexFile;
import io.github.libxposed.api.XposedModule;

/** Modern libxposed API 102 entry. Legacy XposedBridge APIs must not be used by API 102 modules. */
public final class MainHook extends XposedModule {
 private static final String TAG="GmsAntiFraudGuard", ANDROID="android", PHONE_MANAGER="com.coloros.phonemanager", PHONE="com.android.phone", TELECOM="com.android.server.telecom", NATIONAL_ANTI_FRAUD="com.hicorenational.antifraud";
 private static final Set<String> SCOPE=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(ANDROID,PHONE_MANAGER,PHONE,TELECOM,NATIONAL_ANTI_FRAUD)));
 private static final Set<String> GOOGLE_TARGETS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("com.google.android.gms","com.android.vending","com.google.android.gsf")));
 private static final String[] ANTI_FRAUD_COMPONENTS={
  "com.oplus.phonemanager.aivoicecalldetect.antifraudhome.SecurityHomeActivity","com.oplus.phonemanager.aivoicecalldetect.settings.AiVoiceCallDetectSettingsActivity","com.oplus.phonemanager.aivoicecalldetect.riskdetail.ui.AntiFraudSeqDetailsActivity","com.oplus.phonemanager.aivoicecalldetect.antifraudrecords.view.SecurityEventStatisticsActivity","com.oplus.phonemanager.aivoicecalldetect.dialog.FraudRiskDialogActivity","com.oplus.phonemanager.aivoicecalldetect.receiver.InCallRiskDialogReceiver","com.oplus.phonemanager.aivoicecalldetect.trigger.VoipCallDetectTriggerReceiver","com.oplus.phonemanager.aivoicecalldetect.trigger.SimCallDetectTriggerService","com.oplus.phonemanager.aivoicecalldetect.service.AiVoiceDetectForegroundService","com.oplus.phonemanager.aivoicecalldetect.provider.AiVoiceDetectProvider","com.oplus.phonemanager.aivoicecalldetect.provider.FeedbackFileLogProvider","com.oplus.phonemanager.common.provider.FraudDetectRuleFilePipeProvider"};
 private static final String[] FRAUD_TOKENS={"antifraud","anti_fraud","anti fraud","fraud","deepfake","诈骗","反诈"};

 @Override public void onModuleLoaded(ModuleLoadedParam p){ info("loaded in "+p.getProcessName()+" with API "+getApiVersion()); }
 @Override public void onPackageReady(PackageReadyParam p){
  String pkg=p.getPackageName(); if(!SCOPE.contains(pkg))return;
  ClassLoader cl=p.getClassLoader();
  if(ANDROID.equals(pkg)){hookUidPolicy(cl,"android.net.OplusNetworkingControlManager");hookUidPolicy(cl,"android.net.IOplusNetworkingControlManager$Stub$Proxy");hookSystemServer(cl);}
  if(PHONE_MANAGER.equals(pkg)||PHONE.equals(pkg)||TELECOM.equals(pkg))neutralizeFraudCode(cl,pkg,p.getApplicationInfo());
  if(NATIONAL_ANTI_FRAUD.equals(pkg))hookDedicatedAntiFraudApp(cl);
 }
 private void hookSystemServer(ClassLoader cl){try{Method m=findMethod(Class.forName("com.android.server.SystemServer",false,cl),"startOtherServices");hook(m).setId("system-server-start").intercept(chain->{Object r=chain.proceed();try{Field f=chain.getThisObject().getClass().getDeclaredField("mSystemContext");f.setAccessible(true);startEventRepair((Context)f.get(chain.getThisObject()));}catch(Throwable t){error("system context",t);}return r;});}catch(Throwable t){error("SystemServer hook",t);}}
 private void hookUidPolicy(ClassLoader cl,String name){try{Class<?> c=Class.forName(name,false,cl);for(Method m:c.getDeclaredMethods())if(m.getName().equals("setUidPolicy")&&m.getParameterCount()>=2)hook(m).setId("protect-google-uid-"+name).intercept(chain->{Object[] a=chain.getArgs().toArray();if(a[0] instanceof Integer&&a[1] instanceof Integer&&(Integer)a[1]!=0&&isGoogleUid((Integer)a[0])){a[1]=0;info("blocked OEM deny uid="+a[0]);return chain.proceed(a);}return chain.proceed();});}catch(Throwable ignored){}}
 private void neutralizeFraudCode(ClassLoader cl,String pkg,ApplicationInfo ai){
  Set<String> names=new LinkedHashSet<>();scanDex(ai.sourceDir,names);if(ai.splitSourceDirs!=null)for(String path:ai.splitSourceDirs)scanDex(path,names);
  for(String cn:names)if(looksFraud(cn))try{neutralizeClass(Class.forName(cn,false,cl),pkg);}catch(Throwable ignored){}
  if(TELECOM.equals(pkg))try{Class<?> call=Class.forName("com.android.server.telecom.Call",false,cl);Method e=Class.forName("com.android.server.telecom.j6",false,cl).getDeclaredMethod("E",call);hookNeutral(e,"telecom-intercept-center");Method c=Class.forName("com.android.server.telecom.number.j",false,cl).getDeclaredMethod("c");hookNeutral(c,"telecom-is-center");}catch(Throwable t){error("Telecom anti-fraud path",t);}
 }
 private void scanDex(String path,Set<String> out){if(path==null)return;try{DexFile d=new DexFile(path);Enumeration<String> e=d.entries();while(e.hasMoreElements()){String n=e.nextElement();if(looksFraud(n))out.add(n);}d.close();}catch(Throwable t){error("scan "+path,t);}}
 private void neutralizeClass(Class<?> c,String pkg){int count=0;for(Method m:c.getDeclaredMethods())try{hookNeutral(m,"fraud-"+c.getName()+"#"+m.getName()+"-"+count);count++;}catch(Throwable ignored){}if(count>0)info("neutralized "+count+" methods in "+pkg+"/"+c.getName());}
 private void hookNeutral(Method m,String id){final Object value=neutralValue(m.getReturnType());hook(m).setId(id).intercept(chain->value);}
 private static Method findMethod(Class<?> c,String n)throws NoSuchMethodException{for(Method m:c.getDeclaredMethods())if(m.getName().equals(n))return m;throw new NoSuchMethodException(c.getName()+"#"+n);}
 private static boolean looksFraud(String v){String s=v.toLowerCase(Locale.ROOT);for(String t:FRAUD_TOKENS)if(s.contains(t))return true;return false;}
 private static Object neutralValue(Class<?> t){if(t==Void.TYPE)return null;if(t==Boolean.TYPE)return false;if(t==Byte.TYPE)return(byte)0;if(t==Short.TYPE)return(short)0;if(t==Integer.TYPE)return 0;if(t==Long.TYPE)return 0L;if(t==Float.TYPE)return 0f;if(t==Double.TYPE)return 0d;if(t==Character.TYPE)return'\0';return null;}
 private void hookDedicatedAntiFraudApp(ClassLoader cl){try{Method m=Class.forName("android.app.Application",false,cl).getDeclaredMethod("onCreate");hook(m).setId("disable-dedicated-antifraud").intercept(chain->{Object r=chain.proceed();android.app.Application a=(android.app.Application)chain.getThisObject();if(NATIONAL_ANTI_FRAUD.equals(a.getPackageName())){a.getPackageManager().setApplicationEnabledSetting(NATIONAL_ANTI_FRAUD,android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,0);android.os.Process.killProcess(android.os.Process.myPid());}return r;});}catch(Throwable t){error("dedicated anti-fraud block",t);}}
 private boolean isGoogleUid(int uid){try{Object pm=Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null);String[] ps=(String[])pm.getClass().getMethod("getPackagesForUid",int.class).invoke(pm,uid);if(ps!=null)for(String p:ps)if(GOOGLE_TARGETS.contains(p))return true;}catch(Throwable t){error("UID lookup",t);}return false;}
 private void startEventRepair(Context c){Handler h=new Handler(Looper.getMainLooper());h.postDelayed(()->{repair(c);disableAntiFraud(c);},5000);BroadcastReceiver r=new BroadcastReceiver(){@Override public void onReceive(Context x,Intent i){if((Intent.ACTION_PACKAGE_ADDED.equals(i.getAction())||Intent.ACTION_PACKAGE_REPLACED.equals(i.getAction()))&&i.getData()!=null){String pkg=i.getData().getSchemeSpecificPart();if(PHONE_MANAGER.equals(pkg)){h.postDelayed(()->disableAntiFraud(x),1500);return;}if(NATIONAL_ANTI_FRAUD.equals(pkg)){h.postDelayed(()->disablePackage(x,NATIONAL_ANTI_FRAUD),1500);return;}if(!GOOGLE_TARGETS.contains(pkg))return;}h.removeCallbacksAndMessages(null);h.postDelayed(()->repair(x),1500);}};try{IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_BOOT_COMPLETED);f.addAction("android.net.conn.CONNECTIVITY_CHANGE");c.registerReceiver(r,f,Context.RECEIVER_NOT_EXPORTED);IntentFilter p=new IntentFilter();p.addAction(Intent.ACTION_PACKAGE_ADDED);p.addAction(Intent.ACTION_PACKAGE_REPLACED);p.addDataScheme("package");c.registerReceiver(r,p,Context.RECEIVER_NOT_EXPORTED);}catch(Throwable t){error("receiver",t);}}
 private void disablePackage(Context c,String pkg){try{c.getPackageManager().setApplicationEnabledSetting(pkg,android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,0);info("disabled "+pkg);}catch(Throwable t){error("disable "+pkg,t);}}
 private void disableAntiFraud(Context c){android.content.pm.PackageManager pm=c.getPackageManager();for(String cls:ANTI_FRAUD_COMPONENTS)try{ComponentName cn=new ComponentName(PHONE_MANAGER,cls);if(pm.getComponentEnabledSetting(cn)!=android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED)pm.setComponentEnabledSetting(cn,android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,android.content.pm.PackageManager.DONT_KILL_APP);}catch(Throwable t){error("disable anti-fraud "+cls,t);}try{pm.getApplicationInfo(NATIONAL_ANTI_FRAUD,0);disablePackage(c,NATIONAL_ANTI_FRAUD);}catch(Throwable ignored){}}
 private void repair(Context c){try{Object m=c.getSystemService("networking_control");if(m==null)return;Method set=m.getClass().getMethod("setUidPolicy",int.class,int.class);for(String p:GOOGLE_TARGETS)try{set.invoke(m,c.getPackageManager().getPackageUid(p,0),0);}catch(Throwable t){error("repair "+p,t);}}catch(Throwable t){error("manager",t);}}
 private void info(String s){log(Log.INFO,TAG,s);} private void error(String s,Throwable t){log(Log.ERROR,TAG,s,t);}
}
