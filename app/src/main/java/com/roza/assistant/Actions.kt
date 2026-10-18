package com.roza.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.view.KeyEvent
import java.util.Calendar
import java.util.Locale

// Carries out a command and returns the sentence that Rosa shows as her answer.
object Actions {

    fun execute(context: Context, command: Command): String {
        return when (command) {
            is Command.OpenApp -> openApp(context, command.app)
            is Command.SetAlarm -> setAlarm(context, command.hour, command.minute)
            is Command.SetTimer -> setTimer(context, command.seconds)
            is Command.Media -> media(context, command)
            is Command.Volume -> volume(context, command)
            is Command.Navigate -> navigate(context, command)
            is Command.Reply -> command.text
            is Command.ShowDate -> PersianUtils.dateReply(Calendar.getInstance())
            is Command.ShowTime -> PersianUtils.timeReply(Calendar.getInstance())
        }
    }

    private fun start(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openApp(context: Context, app: AppEntry): String {
        return if (AllowedApps.launch(context, app)) {
            "باشه، " + app.title + " را باز کردم."
        } else {
            "برنامه‌ی " + app.title + " روی گوشی پیدا نشد."
        }
    }

    private fun setAlarm(context: Context, hour: Int, minute: Int): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
        intent.putExtra(AlarmClock.EXTRA_HOUR, hour)
        intent.putExtra(AlarmClock.EXTRA_MINUTES, minute)
        intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Rosa")
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val shown = PersianUtils.toPersianDigits(String.format(Locale.US, "%02d:%02d", hour, minute))
        return if (start(context, intent)) {
            "درخواست آلارم ساعت " + shown +
                " را به برنامه‌ی ساعت دادم. بهتر است یک بار آنجا ببینی که ثبت شده."
        } else {
            "برنامه‌ی ساعت گوشی آلارم را نپذیرفت."
        }
    }

    private fun setTimer(context: Context, seconds: Int): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
        intent.putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Rosa")
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val shown = PersianUtils.toPersianDigits((seconds / 60).toString() + " دقیقه و " + (seconds % 60).toString() + " ثانیه")
        return if (start(context, intent)) {
            "تایمر " + shown + " را به برنامه‌ی ساعت دادم."
        } else {
            "برنامه‌ی ساعت گوشی تایمر را نپذیرفت."
        }
    }

    // Sends a media key to whatever player is active right now.
    private fun sendKey(context: Context, keyCode: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    // Sends a media key straight to one app, so it does not end up in another player.
    private fun sendKeyToApp(context: Context, packageName: String, keyCode: Int) {
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            intent.setPackage(packageName)
            intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, keyCode))
            context.sendBroadcast(intent)
        }
    }

    private fun keyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> "بعدی"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "قبلی"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "توقف"
            else -> "پخش"
        }
    }

    private fun media(context: Context, command: Command.Media): String {
        val app = command.app
        if (app != null) {
            // The user named a player (Spotify or Music): talk to that player only.
            val pkg = AllowedApps.findPackage(context, app)
                ?: return "برنامه‌ی " + app.title + " روی گوشی پیدا نشد."
            if (command.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!AllowedApps.launch(context, app)) {
                    return "نتوانستم " + app.title + " را باز کنم."
                }
                val appContext = context.applicationContext
                Handler(Looper.getMainLooper()).postDelayed({
                    sendKeyToApp(appContext, pkg, command.keyCode)
                }, 2500)
                return app.title + " را باز کردم و چند ثانیه بعد دستور «پخش» را مستقیم به همان برنامه می‌فرستم. اگر پخش نشد، بگو."
            }
            sendKeyToApp(context, pkg, command.keyCode)
            return "دستور «" + keyName(command.keyCode) + "» را مستقیم به " + app.title + " فرستادم."
        }
        sendKey(context, command.keyCode)
        return "دستور «" + keyName(command.keyCode) + "» را به پخش‌کننده‌ی فعلی فرستادم."
    }

    private fun volume(context: Context, command: Command.Volume): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (command.up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        for (i in 1..command.steps) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        }
        // Show the volume bar once at the end.
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val percent = if (max > 0) now * 100 / max else 0
        val word = if (command.up) "زیاد" else "کم"
        return PersianUtils.toPersianDigits(
            "صدای موسیقی " + command.steps.toString() + " پله " + word +
                " شد. الان " + percent.toString() + " درصد است."
        )
    }

    private fun navigate(context: Context, command: Command.Navigate): String {
        val place = command.place
        val app = command.app ?: AllowedApps.firstInstalled(context, listOf("neshan", "balad", "waze"))
            ?: return "هیچ‌کدام از نشان، بلد و ویز روی گوشی پیدا نشد."
        val pkg = AllowedApps.findPackage(context, app)
            ?: return "برنامه‌ی " + app.title + " روی گوشی پیدا نشد."

        val intent = if (app.key == "waze") {
            Intent(Intent.ACTION_VIEW, Uri.parse("waze://?q=" + Uri.encode(place) + "&navigate=yes"))
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)))
        }
        intent.setPackage(pkg)
        if (start(context, intent)) {
            return "مقصد «" + place + "» را در " + app.title + " باز کردم."
        }
        // The app did not accept the request, so just open it.
        return if (AllowedApps.launch(context, app)) {
            app.title + " جست‌وجوی مقصد را نپذیرفت. برنامه را باز کردم، مقصد «" + place + "» را خودت جست‌وجو کن."
        } else {
            "نتوانستم " + app.title + " را باز کنم."
        }
    }
}
