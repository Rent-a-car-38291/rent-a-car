package com.avans.rentacar.start

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.avans.rentacar.cars.Car
import com.avans.rentacar.components.OutlineCard
import com.avans.rentacar.components.PrimaryButton
import com.avans.rentacar.components.SecondaryButton
import com.avans.rentacar.components.drawWave
import com.avans.rentacar.prefersReducedMotion
import com.avans.rentacar.theme.Acid
import com.avans.rentacar.theme.AcidShapes
import com.avans.rentacar.theme.AcidTheme
import com.avans.rentacar.theme.Ink
import com.avans.rentacar.theme.Pink
import com.avans.rentacar.theme.Violet
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The rotating part of the headline, each on its own color block. */
val Dayparts = listOf("morning" to Acid, "afternoon" to Pink, "evening" to Violet)

/** One row of cards takes this long to drift past. */
private const val RowSeconds = 30f

/**
 * The first screen for someone who is not logged in. Wrap it in a dark [AcidTheme]:
 * it follows the black intro, a switch to light would flash.
 */
@Composable
fun WelcomeScreen(
    cars: List<Car>,
    onCar: (Int) -> Unit,
    onBrowse: () -> Unit,
    onCreateAccount: () -> Unit,
    onLogIn: () -> Unit,
) {
    val reduced = prefersReducedMotion()
    val c = AcidTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(vertical = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("rent a car", style = AcidTheme.type.mono, color = c.muted)
            Headline(rotate = !reduced)
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DriftingRow(cars, reverse = false, moving = !reduced, onCar) { Chip("€${it.rate.roundToInt()}", Acid) }
            DriftingRow(cars.filter { it.averageRating != null }.reversed(), reverse = true, moving = !reduced, onCar) {
                Chip("${oneDecimal(it.averageRating!!)} ★", Pink)
            }
        }
        SwayingWave(sway = !reduced, modifier = Modifier.padding(vertical = 20.dp))
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryButton("See ${cars.size} cars nearby", onBrowse, Modifier.fillMaxWidth())
            SecondaryButton("Create account", onCreateAccount, Modifier.fillMaxWidth())
            Text(
                buildAnnotatedString {
                    append("Already have an account? ")
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append("Log in") }
                },
                style = AcidTheme.type.label,
                modifier = Modifier
                    .defaultMinSize(minHeight = AcidShapes.minTouch)
                    .clickable(onClick = onLogIn)
                    .padding(horizontal = 12.dp, vertical = 13.dp),
            )
        }
    }
}

/** "a car for the morning", the daypart rotating every few seconds unless [rotate] is off. */
@Composable
private fun Headline(rotate: Boolean) {
    var part by remember { mutableIntStateOf(0) }
    if (rotate) LaunchedEffect(Unit) {
        while (true) {
            delay(2800)
            part = nextDaypart(part)
        }
    }
    val style = AcidTheme.type.headline
    Column {
        Text("a car for", style = style)
        AnimatedContent(
            targetState = part,
            transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
        ) { i ->
            val (word, color) = Dayparts[i]
            Text(
                "the $word",
                style = style,
                color = Ink,
                modifier = Modifier.padding(top = 4.dp).background(color, AcidShapes.badge).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

fun nextDaypart(part: Int) = (part + 1) % Dayparts.size

/**
 * An endless row of cars. With [moving] it drifts by itself, left or right with [reverse];
 * it can always be swiped by hand.
 */
@Composable
private fun DriftingRow(
    cars: List<Car>,
    reverse: Boolean,
    moving: Boolean,
    onCar: (Int) -> Unit,
    chip: @Composable (Car) -> Unit,
) {
    // Start in the middle of a very long list, so it can drift either way for hours.
    val middle = Int.MAX_VALUE / 2
    val state = rememberLazyListState(initialFirstVisibleItemIndex = middle - middle % cars.size)
    if (moving) LaunchedEffect(state) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) {
                val px = state.layoutInfo.viewportSize.width * (now - last) / 1e9f / RowSeconds
                state.dispatchRawDelta(if (reverse) -px else px)
            }
            last = now
        }
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(Int.MAX_VALUE) { i ->
            val car = cars[i % cars.size]
            CarCard(car, onClick = { onCar(car.id) }) { chip(car) }
        }
    }
}

@Composable
private fun CarCard(car: Car, onClick: () -> Unit, chip: @Composable () -> Unit) {
    OutlineCard(Modifier.width(156.dp), onClick = onClick) {
        // shortcut: a block in the photo color until the API serves car.photoUrl
        Box(Modifier.fillMaxWidth().height(96.dp).background(AcidTheme.colors.photo).padding(8.dp)) { chip() }
        Text(car.brand, style = AcidTheme.type.label, maxLines = 1, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text,
        style = AcidTheme.type.label,
        color = Ink,
        modifier = Modifier.background(color, AcidShapes.badge).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** A lime wave that slowly swells and settles. */
@Composable
private fun SwayingWave(sway: Boolean, modifier: Modifier = Modifier) {
    val amplitude = if (sway) {
        rememberInfiniteTransition().animateFloat(4f, 9f, infiniteRepeatable(tween(2400), RepeatMode.Reverse)).value
    } else 6f
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        drawWave(Acid, amplitude = amplitude.dp, wavelength = 60.dp, strokeWidth = 3.dp)
    }
}

/** 4.25 becomes "4.3"; Kotlin common has no String.format. */
private fun oneDecimal(x: Double): String {
    val tenths = (x * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
