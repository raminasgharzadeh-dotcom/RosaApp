package com.roza.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

// One app that Rosa is allowed to use.
data class AppEntry(
    val key: String,
    val title: String,
    val aliases: List<String>,      // how you may say its name
    val labels: List<String>,       // words to look for in the installed app's name
    val exactLabel: Boolean,        // true: the installed app's name must equal a label
    val packageHint: String?        // known package name, if any
)

// The apps Rosa may open. Anything that is not in this list is never opened.
object AllowedApps {

    val all: List<AppEntry> = listOf(
        AppEntry(
            "whatsapp", "واتساپ",
            listOf("واتساپ", "واتس اپ", "واتز اپ", "whatsapp", "whats app"),
            listOf("whatsapp"), false, "com.whatsapp"
        ),
        AppEntry(
            "messages", "پیام‌ها (Google Messages)",
            listOf("گوگل مسیج", "گوگل مسیجز", "مسیج", "مسیجز", "پیام ها", "google messages", "messages"),
            listOf("messages", "پیام‌ها"), true, "com.google.android.apps.messaging"
        ),
        AppEntry(
            "contacts", "مخاطبین",
            listOf("مخاطبین", "کانتکت", "کانتکتس", "contacts"),
            listOf("contacts", "مخاطبین"), true, null
        ),
        AppEntry(
            "phone", "تلفن",
            listOf("تلفن", "فون", "شماره گیر", "phone", "dialer"),
            listOf("phone", "dialer", "تلفن"), true, null
        ),
        AppEntry(
            "music", "Music",
            listOf("موزیک", "میوزیک", "موسیقی", "music"),
            listOf("music"), true, null
        ),
        AppEntry(
            "spotify", "Spotify",
            listOf("اسپاتیفای", "اسپوتیفای", "اسپاتیفی", "spotify"),
            listOf("spotify"), false, "com.spotify.music"
        ),
        AppEntry(
            "clock", "ساعت",
            listOf("ساعت", "ساعت گوشی", "clock"),
            listOf("clock", "ساعت"), true, null
        ),
        AppEntry(
            "accuweather", "AccuWeather",
            listOf("اکیوودر", "اکیو ودر", "اکو ودر", "accuweather"),
            listOf("accuweather"), false, "com.accuweather.android"
        ),
        AppEntry(
            "neshan", "نشان",
            listOf("نشان", "neshan"),
            listOf("neshan", "نشان"), false, null
        ),
        AppEntry(
            "balad", "بلد",
            listOf("بلد", "balad"),
            listOf("balad", "بلد"), false, null
        ),
        AppEntry(
            "waze", "Waze",
            listOf("ویز", "waze"),
            listOf("waze"), false, "com.waze"
        )
    )

    fun byKey(key: String): AppEntry? = all.firstOrNull { it.key == key }

    // The first allowed app (among the given keys, or all of them) that the text names.
    fun mentioned(text: String, keys: List<String>?): AppEntry? {
        val padded = " " + text + " "
        for (app in all) {
            if (keys != null && !keys.contains(app.key)) continue
            for (alias in app.aliases) {
                if (padded.contains(" " + PersianUtils.normalize(alias) + " ")) return app
            }
        }
        return null
    }

    private fun launcherApps(context: Context): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
    }

    // The package name of the installed app, or null if it is not on the phone.
    fun findPackage(context: Context, app: AppEntry): String? {
        val infos = launcherApps(context)
        val pm = context.packageManager
        val hint = app.packageHint
        if (hint != null) {
            for (info in infos) {
                if (info.activityInfo.packageName == hint) return hint
            }
        }
        for (info in infos) {
            val label = info.loadLabel(pm).toString().trim().lowercase()
            for (word in app.labels) {
                val w = word.lowercase()
                val matches = if (app.exactLabel) label == w else label.contains(w)
                if (matches) return info.activityInfo.packageName
            }
        }
        return null
    }

    fun firstInstalled(context: Context, keys: List<String>): AppEntry? {
        for (key in keys) {
            val app = byKey(key) ?: continue
            if (findPackage(context, app) != null) return app
        }
        return null
    }

    fun launch(context: Context, app: AppEntry): Boolean {
        val pkg = findPackage(context, app) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
