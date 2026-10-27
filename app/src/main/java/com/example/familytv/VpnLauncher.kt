package com.example.familytv

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** يفتح تطبيق VPN المثبّت على الجهاز. أندرويد لا يسمح لتطبيق بتشغيل VPN تطبيق آخر، فنفتحه ويضغط المستخدم "اتصال". */
object VpnLauncher {
    /** أشهر تطبيقات VPN (يجب أن تكون أيضاً في <queries> داخل AndroidManifest). */
    val known = listOf(
        "ProtonVPN" to "ch.protonvpn.android",
        "WireGuard" to "com.wireguard.android",
        "OpenVPN for Android" to "de.blinkt.openvpn",
        "OpenVPN Connect" to "net.openvpn.openvpn",
        "Cloudflare WARP" to "com.cloudflare.onedotonedotonedotone",
        "Windscribe" to "com.windscribe.vpn",
        "NordVPN" to "com.nordvpn.android",
        "ExpressVPN" to "com.expressvpn.vpn",
        "Surfshark" to "com.surfshark.vpnclient.android",
        "Mullvad VPN" to "net.mullvad.mullvadvpn",
        "Hotspot Shield" to "hotspotshield.android.vpn",
        "TunnelBear" to "com.tunnelbear.android",
        "Turbo VPN" to "free.vpn.unblock.proxy.turbovpn",
        "Psiphon" to "com.psiphon3",
        "Hide.me" to "com.hideme.android"
    )

    fun installed(ctx: Context): List<Pair<String, String>> =
        known.filter { ctx.packageManager.getLaunchIntentForPackage(it.second) != null }

    fun launch(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); true } catch (_: Exception) { false }
    }

    /** إعدادات VPN في النظام (قد لا تتوفر في بعض أجهزة التلفاز). */
    fun openSystemSettings(ctx: Context): Boolean {
        for (action in listOf(Settings.ACTION_VPN_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)) {
            try {
                ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {}
        }
        return false
    }
}

@Composable
fun VpnDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { VpnLauncher.installed(ctx) }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("فتح VPN") },
        text = {
            Column {
                if (apps.isEmpty()) {
                    Text("لم يُعثر على تطبيق VPN معروف على هذا الجهاز. يمكنك فتح إعدادات VPN للنظام بدلاً من ذلك.")
                } else {
                    Text("اختر تطبيق VPN الخاص بك، واتصل، ثم عد إلى FamilyTV:")
                    apps.forEach { (name, pkg) ->
                        Text(
                            name,
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (VpnLauncher.launch(ctx, pkg)) onClose() else toast("تعذر فتح $name")
                            }.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (VpnLauncher.openSystemSettings(ctx)) onClose() else toast("لا تحتوي هذا الجهاز على شاشة إعدادات VPN")
            }) { Text("إعدادات VPN للنظام") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("إغلاق") } }
    )
}
