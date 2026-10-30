package com.gnayoah.quickwallet;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 双击电源键 -> Google Wallet（HyperOS）。
 *
 * 思路：
 * 1. 小米自己的按键规则会按 "double_click_power_key" 这个设置项查要执行的功能，
 *    我们在 getFunction 里直接把结果换成钱包功能，不去改系统设置，卸载后自动恢复。
 * 2. 小米的钱包功能最终会走 ShortCutActionsUtils.launchDefaultWallet，
 *    这里改成打开 Google Wallet。
 * 3. 让 Wallet 的付款界面能显示在锁屏上方。
 * 4. 另外保留一个 AOSP 的 powerPress 兜底，万一小米那条路没走通。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "QuickWallet";

    private static final String WALLET_PKG = "com.google.android.apps.walletnfcrel";
    private static final String WALLET_ACTIVITY =
            "com.google.android.apps.wallet.pmode.PresentationModeActivity";
    private static final String WALLET_ACTION = "com.google.android.apps.wallet.main.QUICKDRAW";

    private static final String KEY_DOUBLE_POWER = "double_click_power_key";
    private static final String FUNC_WALLET = "launch_QAW_wallet";

    // 多条路径可能同时命中，800ms 内只启动一次
    private static long lastLaunchTime = 0;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }
        ClassLoader cl = lpparam.classLoader;
        hookKeyRule(cl);
        hookWalletAction(cl);
        hookLockScreen(cl);
        hookAospPowerPress(cl);
    }

    // 1. 把双击电源键的功能换成钱包
    private void hookKeyRule(ClassLoader cl) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (KEY_DOUBLE_POWER.equals(param.args[0])) {
                    param.setResult(FUNC_WALLET);
                }
            }
        };
        String[] names = {
                "com.android.server.policy.MiuiSingleKeyRule",
                "com.android.server.input.shortcut.singlekeyrule.PowerKeyRule"
        };
        int count = 0;
        for (String name : names) {
            Class<?> clazz = XposedHelpers.findClassIfExists(name, cl);
            if (clazz == null) {
                continue;
            }
            for (Method m : clazz.getDeclaredMethods()) {
                Class<?>[] types = m.getParameterTypes();
                if ("getFunction".equals(m.getName()) && types.length == 1 && types[0] == String.class) {
                    XposedBridge.hookMethod(m, hook);
                    count++;
                }
            }
        }
        XposedBridge.log(TAG + ": getFunction hooked x" + count);
    }

    // 2. 小米的"打开默认钱包"改成打开 Google Wallet
    private void hookWalletAction(ClassLoader cl) {
        Class<?> clazz = XposedHelpers.findClassIfExists(
                "com.miui.server.input.util.ShortCutActionsUtils", cl);
        if (clazz == null) {
            XposedBridge.log(TAG + ": ShortCutActionsUtils not found");
            return;
        }
        int count = 0;
        for (Method m : clazz.getDeclaredMethods()) {
            if ("launchDefaultWallet".equals(m.getName())) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (launchWallet(param.thisObject)) {
                            Method method = (Method) param.method;
                            param.setResult(method.getReturnType() == boolean.class ? Boolean.TRUE : null);
                        }
                    }
                });
                count++;
            }
        }
        XposedBridge.log(TAG + ": launchDefaultWallet hooked x" + count);
    }

    // 3. 允许 Wallet 的付款界面显示在锁屏上方（仍然需要 Wallet 自己的指纹/PIN 验证）
    private void hookLockScreen(ClassLoader cl) {
        try {
            Class<?> record = XposedHelpers.findClassIfExists("com.android.server.wm.ActivityRecord", cl);
            if (record == null) {
                XposedBridge.log(TAG + ": ActivityRecord not found");
                return;
            }
            XposedHelpers.findAndHookMethod(record, "canShowWhenLocked", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(param.getResult())) {
                        return;
                    }
                    Object name = XposedHelpers.getObjectField(param.thisObject, "mActivityComponent");
                    if (name instanceof ComponentName) {
                        ComponentName cn = (ComponentName) name;
                        if (WALLET_PKG.equals(cn.getPackageName())
                                && WALLET_ACTIVITY.equals(cn.getClassName())) {
                            param.setResult(true);
                        }
                    }
                }
            });
            XposedBridge.log(TAG + ": canShowWhenLocked hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": lock screen hook failed " + t);
        }
    }

    // 4. 兜底：AOSP 的 powerPress，按键次数为 2 时直接打开
    private void hookAospPowerPress(ClassLoader cl) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    // 不同版本参数顺序不同，取第一个 int 参数当按键次数
                    int count = -1;
                    for (Object arg : param.args) {
                        if (arg instanceof Integer) {
                            count = (Integer) arg;
                            break;
                        }
                    }
                    if (count == 2 && launchWallet(param.thisObject)) {
                        param.setResult(null);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": powerPress hook error " + t);
                }
            }
        };
        String[] names = {
                "com.android.server.policy.PhoneWindowManager",
                "com.android.server.policy.MiuiPhoneWindowManager"
        };
        for (String name : names) {
            Class<?> clazz = XposedHelpers.findClassIfExists(name, cl);
            if (clazz == null) {
                continue;
            }
            int count = 0;
            for (Method m : clazz.getDeclaredMethods()) {
                if ("powerPress".equals(m.getName())) {
                    XposedBridge.hookMethod(m, hook);
                    count++;
                }
            }
            XposedBridge.log(TAG + ": " + name + " powerPress hooked x" + count);
        }
    }

    /**
     * 打开 Google Wallet。返回 true 表示已经处理（包括 800ms 内的重复触发）。
     * owner 是持有 mContext 字段的系统对象。
     */
    private static boolean launchWallet(Object owner) {
        try {
            long now = System.currentTimeMillis();
            if (now - lastLaunchTime < 800) {
                return true;
            }

            Context context = (Context) XposedHelpers.getObjectField(owner, "mContext");
            PackageManager pm = context.getPackageManager();

            // 优先直接进付款界面，找不到就退回普通启动入口
            Intent intent = new Intent(WALLET_ACTION).setClassName(WALLET_PKG, WALLET_ACTIVITY);
            int flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NO_HISTORY
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
            if (pm.resolveActivity(intent, 0) == null) {
                intent = pm.getLaunchIntentForPackage(WALLET_PKG);
                flags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED;
            }
            if (intent == null) {
                XposedBridge.log(TAG + ": Google Wallet is not installed");
                return false;
            }
            intent.addFlags(flags);

            XposedHelpers.callMethod(context, "startActivityAsUser", intent,
                    XposedHelpers.getStaticObjectField(UserHandle.class, "CURRENT"));
            lastLaunchTime = now;
            return true;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": launch failed " + t);
            return false;
        }
    }
}
