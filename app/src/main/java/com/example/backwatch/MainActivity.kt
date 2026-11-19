package com.example.backwatch

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay

// Pose = 14 floats on a 100x100 grid (y down, floor ~80):
// head, shoulder, hip, knee, foot, elbow, hand (x,y each)
class Ex(val name: String, val sided: Boolean, val a: FloatArray, val b: FloatArray)
class Step(val title: String, val sub: String, val secs: Int, val ex: Ex?, val mirror: Boolean)

private fun p(vararg v: Float) = v

private val LYING = p(14f,72f, 26f,74f, 52f,74f, 66f,56f, 76f,74f, 36f,76f, 46f,76f)
private val QUAD = p(20f,44f, 30f,48f, 65f,48f, 65f,80f, 82f,80f, 30f,64f, 30f,80f)

// Ordered: mobility -> strength -> stretch
val EXERCISES = listOf(
    Ex("Pelvic tilt", false, LYING, p(14f,72f, 26f,74f, 52f,71f, 66f,53f, 76f,74f, 36f,76f, 46f,76f)),
    Ex("Cat-cow", false, p(18f,40f, 30f,52f, 65f,50f, 65f,80f, 82f,80f, 30f,66f, 30f,80f),
        p(22f,54f, 30f,44f, 65f,44f, 65f,80f, 82f,80f, 30f,62f, 30f,80f)),
    Ex("Knee to chest", true, LYING, p(14f,72f, 26f,74f, 52f,74f, 40f,58f, 52f,68f, 36f,68f, 42f,60f)),
    Ex("Bird-dog", true, QUAD, p(18f,42f, 30f,48f, 65f,48f, 80f,48f, 96f,48f, 18f,47f, 5f,46f)),
    Ex("Glute bridge", false, LYING, p(14f,72f, 26f,74f, 52f,56f, 68f,52f, 76f,74f, 36f,76f, 46f,76f)),
    Ex("Dead bug", true, p(14f,72f, 26f,74f, 52f,74f, 52f,52f, 66f,52f, 26f,62f, 26f,50f),
        p(14f,72f, 26f,74f, 52f,74f, 68f,66f, 86f,72f, 16f,73f, 6f,72f)),
    Ex("Side plank", true, p(14f,58f, 24f,62f, 55f,72f, 70f,76f, 86f,80f, 24f,80f, 34f,80f),
        p(14f,56f, 24f,62f, 55f,62f, 70f,70f, 86f,80f, 24f,80f, 34f,80f)),
    Ex("Child's pose", false, p(16f,60f, 28f,62f, 60f,56f, 62f,80f, 82f,80f, 24f,72f, 14f,80f),
        p(12f,74f, 24f,72f, 66f,68f, 64f,80f, 82f,80f, 16f,78f, 6f,80f)),
    Ex("Figure-4 stretch", true, LYING, p(14f,72f, 26f,74f, 52f,74f, 46f,58f, 60f,62f, 38f,70f, 48f,64f)),
    Ex("Hip flexor stretch", true, p(46f,22f, 46f,36f, 46f,58f, 66f,62f, 66f,80f, 42f,48f, 40f,58f),
        p(52f,24f, 52f,38f, 54f,62f, 70f,64f, 70f,80f, 48f,50f, 46f,60f))
)

const val SIDE_SECS = 30
const val SOLO_SECS = 40
const val REST_SECS = 10
const val SWITCH_SECS = 4

fun buildPlan(totalSec: Int): List<Step> {
    val n = maxOf(3, Math.round(totalSec / 85f))
    val items = (0 until n).map { EXERCISES[(it * EXERCISES.size / n) % EXERCISES.size] }
    val steps = mutableListOf<Step>()
    items.forEachIndexed { i, ex ->
        if (ex.sided) {
            steps += Step(ex.name, "Left side", SIDE_SECS, ex, false)
            steps += Step("Switch sides", "Next: ${ex.name}", SWITCH_SECS, null, false)
            steps += Step(ex.name, "Right side", SIDE_SECS, ex, true)
        } else {
            steps += Step(ex.name, "Hold / flow", SOLO_SECS, ex, false)
        }
        if (i < items.size - 1) {
            val nx = items[i + 1].name
            steps += Step("Rest", "Next: $nx", REST_SECS, null, false)
        }
    }
    return steps
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val vib = getSystemService(Vibrator::class.java)
        val buzz = { vib?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE)); Unit }
        setContent { MaterialTheme { App(buzz) } }
    }
}

@Composable
fun App(buzz: () -> Unit) {
    var plan by remember { mutableStateOf<List<Step>?>(null) }
    val current = plan
    if (current == null) {
        Setup { minutes -> plan = buildPlan(minutes * 60) }
    } else {
        Workout(current, buzz) { plan = null }
    }
}

@Composable
fun Setup(onPick: (Int) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Text("Total time", fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 10).forEach { m ->
                Button(onClick = { onPick(m) }, Modifier.size(54.dp)) { Text("$m") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 20).forEach { m ->
                Button(onClick = { onPick(m) }, Modifier.size(54.dp)) { Text("$m") }
            }
        }
        Text("minutes", fontSize = 11.sp)
    }
}

@Composable
fun Workout(plan: List<Step>, buzz: () -> Unit, onExit: () -> Unit) {
    var idx by remember { mutableStateOf(0) }
    var left by remember { mutableStateOf(plan[0].secs) }
    var paused by remember { mutableStateOf(false) }

    LaunchedEffect(idx, paused) {
        if (paused || idx >= plan.size) return@LaunchedEffect
        while (left > 0) { delay(1000); left-- }
        buzz()
        if (idx < plan.size - 1) { idx++; left = plan[idx].secs } else idx = plan.size
    }

    if (idx >= plan.size) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("Done!", fontSize = 22.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onExit) { Text("OK") }
        }
        return
    }

    val step = plan[idx]
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(step.title, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 1)
        if (step.ex != null) {
            Figure(step.ex, step.mirror, Modifier.size(78.dp))
        } else {
            Spacer(Modifier.height(78.dp))
        }
        Text(step.sub, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
        Text("%d:%02d".format(left / 60, left % 60), fontSize = 26.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { paused = !paused }, Modifier.size(34.dp)) { Text(if (paused) ">" else "II", fontSize = 12.sp) }
            Button(onClick = {
                if (idx < plan.size - 1) { idx++; left = plan[idx].secs } else idx = plan.size
            }, Modifier.size(34.dp)) { Text(">>", fontSize = 12.sp) }
            Button(onClick = onExit, Modifier.size(34.dp)) { Text("X", fontSize = 12.sp) }
        }
    }
}

@Composable
fun Figure(ex: Ex, mirror: Boolean, modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "fig").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "t"
    )
    Canvas(modifier) {
        val s = size.minDimension / 100f
        fun pt(i: Int): Offset {
            var x = lerp(ex.a[i * 2], ex.b[i * 2], t)
            val y = lerp(ex.a[i * 2 + 1], ex.b[i * 2 + 1], t)
            if (mirror) x = 100f - x
            return Offset(x * s, y * s)
        }
        val w = 4.dp.toPx()
        val c = Color.White
        fun line(a: Int, b: Int) = drawLine(c, pt(a), pt(b), w, StrokeCap.Round)
        drawLine(Color.Gray, Offset(0f, 82f * s), Offset(100f * s, 82f * s), 2.dp.toPx())
        line(1, 2); line(2, 3); line(3, 4); line(1, 5); line(5, 6)
        drawCircle(c, 6f * s, pt(0))
    }
}
