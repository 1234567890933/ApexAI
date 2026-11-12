package com.apex.ai.platform;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.text.TextUtils;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain Java, on purpose.
 *
 * This is the layer that talks to the platform's package APIs. Java is the
 * native language of those APIs — the overloads, the deprecated-but-still-needed
 * branches for pre-API-28 devices, and the checked exception surface all read
 * more honestly here than through a Kotlin wrapper. Kotlin calls in from
 * [com.apex.ai.core.AppFacts]; nothing else in the app touches PackageManager
 * directly.
 */
public final class PackageInspector {

    private PackageInspector() {
    }

    /** Everything the About screen needs about this installation. */
    public static Map<String, String> describeSelf(Context context) {
        Map<String, String> facts = new LinkedHashMap<>();
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            PackageInfo info = pm.getPackageInfo(packageName, 0);

            facts.put("package", packageName);
            facts.put("versionName", info.versionName == null ? "unknown" : info.versionName);
            facts.put("versionCode", String.valueOf(versionCodeOf(info)));
            facts.put("targetSdk", String.valueOf(context.getApplicationInfo().targetSdkVersion));
            facts.put("minSdk", String.valueOf(context.getApplicationInfo().minSdkVersion));
            facts.put("abis", TextUtils.join(", ", Build.SUPPORTED_ABIS));
            facts.put("installer", installerOf(pm, packageName));
            facts.put("firstInstall", String.valueOf(info.firstInstallTime));
            facts.put("lastUpdate", String.valueOf(info.lastUpdateTime));
            facts.put("signingSha256", signingSha256(pm, packageName));
        } catch (Throwable t) {
            facts.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        return facts;
    }

    private static long versionCodeOf(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        }
        //noinspection deprecation
        return info.versionCode;
    }

    private static String installerOf(PackageManager pm, String packageName) {
        try {
            String installer = pm.getInstallerPackageName(packageName);
            return installer == null ? "sideloaded" : installer;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static String signingSha256(PackageManager pm, String packageName) {
        try {
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) {
                    signatures = new Signature[0];
                } else if (info.signingInfo.hasMultipleSigners()) {
                    signatures = info.signingInfo.getApkContentsSigners();
                } else {
                    signatures = info.signingInfo.getSigningCertificateHistory();
                }
            } else {
                //noinspection deprecation
                PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                //noinspection deprecation
                signatures = info.signatures == null ? new Signature[0] : info.signatures;
            }
            if (signatures.length == 0) {
                return "none";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(signatures[0].toByteArray());
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (Throwable t) {
            return "unavailable";
        }
    }
}
