package com.md12ali.sakinah.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.md12ali.sakinah.audio.AdhanPlaybackService
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.ui.components.GeometricPattern
import com.md12ali.sakinah.ui.components.MosqueSilhouette
import com.md12ali.sakinah.ui.components.StarField
import com.md12ali.sakinah.ui.theme.Gold
import com.md12ali.sakinah.ui.theme.GoldSoft
import com.md12ali.sakinah.ui.theme.SakinahTheme
import com.md12ali.sakinah.ui.theme.arabicStyle
import com.md12ali.sakinah.ui.theme.skyFor

/** Full-screen Adhan screen shown over the lock screen while the Adhan plays (like an alarm clock). */
class AdhanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() } ?: Prayer.DHUHR
        val time = intent.getStringExtra(EXTRA_TIME).orEmpty()
        val place = intent.getStringExtra(EXTRA_PLACE).orEmpty()

        setContent {
            SakinahTheme(dark = true) {
                AdhanScreen(prayer, time, place, onStop = {
                    AdhanPlaybackService.stop(this)
                    finish()
                }, onEnded = { finish() })
            }
        }
    }

    companion object {
        private const val EXTRA_PRAYER = "p"; private const val EXTRA_TIME = "t"; private const val EXTRA_PLACE = "pl"
        fun intent(context: Context, prayer: Prayer, time: String, place: String) =
            Intent(context, AdhanActivity::class.java)
                .putExtra(EXTRA_PRAYER, prayer.name).putExtra(EXTRA_TIME, time).putExtra(EXTRA_PLACE, place)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun AdhanScreen(prayer: Prayer, time: String, place: String, onStop: () -> Unit, onEnded: () -> Unit) {
    val playing by AdhanPlaybackService.playing.collectAsState()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (playing != null) started = true
        else if (started) onEnded()
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.92f, 1.08f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "p"
    )
    Box(Modifier.fillMaxSize().background(skyFor(if (prayer == Prayer.FAJR || prayer == Prayer.ISHA) Prayer.ISHA else prayer))) {
        GeometricPattern(Modifier.fillMaxSize())
        StarField(Modifier.fillMaxSize())
        MosqueSilhouette(Modifier.fillMaxWidth().height(220.dp).align(Alignment.BottomCenter), color = Color.White.copy(alpha = 0.10f))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("AZAN", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier.size(180.dp).scale(pulse).background(Gold.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(prayer.arabic, style = arabicStyle(46), color = Gold)
            }
            Spacer(Modifier.height(28.dp))
            Text("It's time for ${prayer.english}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
            if (time.isNotBlank()) Text(time, color = GoldSoft, fontSize = 22.sp, modifier = Modifier.padding(top = 4.dp))
            if (place.isNotBlank()) Text(place, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Text("حَيَّ عَلَى الصَّلَاةِ", style = arabicStyle(22), color = Color.White.copy(alpha = 0.9f))
            Text("Come to prayer", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(56.dp))
            Button(
                onClick = onStop,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF1F1A08)),
                modifier = Modifier.fillMaxWidth(0.8f).height(60.dp)
            ) {
                Icon(Icons.Outlined.Stop, null)
                Spacer(Modifier.size(8.dp))
                Text("Stop Adhan", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
