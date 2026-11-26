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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avans.rentacar.cars.Car
import com.avans.rentacar.components.OutlineCard
import com.avans.rentacar.components.PrimaryButton
import com.avans.rentacar.components.SecondaryButton
import com.avans.rentacar.components.SidebarBreakpoint
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
 * The first screen for someone who is not logged in. Follows the device theme, like the intro before it.
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
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (maxWidth >= SidebarBreakpoint) {
            // Wide (web, tablet): words and buttons on the left, the cars fill the right half.
            // "the afternoon" is about 10 em wide: shrink the type so it stays on one line in the left column.
            val column = maxWidth / 2.2f - 128.dp
            val size = minOf(64f, (column - 16.dp).value / 10f)
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = 64.dp, vertical = 48.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("rent a car", style = AcidTheme.type.mono, color = c.muted)
                    Spacer(Modifier.height(16.dp))
                    Headline(rotate = !reduced, style = AcidTheme.type.display.copy(fontSize = size.sp, lineHeight = (size * 1.05f).sp))
                    SwayingWave(sway = !reduced, modifier = Modifier.widthIn(max = 380.dp).padding(vertical = 36.dp))
                    Actions(cars.size, onBrowse, onCreateAccount, onLogIn, Modifier.widthIn(max = 380.dp))
                }
                CarRows(cars, reduced, onCar, cardWidth = 220.dp, Modifier.weight(1.2f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(vertical = 24.dp)) {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("rent a car", style = AcidTheme.type.mono, color = c.muted)
                    Headline(rotate = !reduced, style = AcidTheme.type.headline)
                }
                Spacer(Modifier.weight(1f))
                CarRows(cars, reduced, onCar, cardWidth = 156.dp)
                SwayingWave(sway = !reduced, modifier = Modifier.padding(vertical = 20.dp))
                Actions(cars.size, onBrowse, onCreateAccount, onLogIn, Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

/** The two rows drifting in opposite directions: prices on top, ratings below. */
@Composable
private fun CarRows(cars: List<Car>, reduced: Boolean, onCar: (Int) -> Unit, cardWidth: Dp, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        DriftingRow(cars, reverse = false, moving = !reduced, cardWidth, onCar) { Chip("€${it.rate.roundToInt()}", Acid) }
        DriftingRow(cars.filter { it.averageRating != null }.reversed(), reverse = true, moving = !reduced, cardWidth, onCar) {
            Chip("${oneDecimal(it.averageRating!!)} ★", Pink)
        }
    }
}

@Composable
private fun Actions(count: Int, onBrowse: () -> Unit, onCreateAccount: () -> Unit, onLogIn: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrimaryButton("See $count cars nearby", onBrowse, Modifier.fillMaxWidth())
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

/** "a car for the morning", the daypart rotating every few seconds unless [rotate] is off. */
@Composable
private fun Headline(rotate: Boolean, style: TextStyle) {
    var part by remember { mutableIntStateOf(0) }
    if (rotate) LaunchedEffect(Unit) {
        while (true) {
            delay(2800)
            part = nextDaypart(part)
        }
    }
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
    cardWidth: Dp,
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
            CarCard(car, cardWidth, onClick = { onCar(car.id) }) { chip(car) }
        }
    }
}

@Composable
private fun CarCard(car: Car, width: Dp, onClick: () -> Unit, chip: @Composable () -> Unit) {
    OutlineCard(Modifier.width(width), onClick = onClick) {
        // shortcut: a block in the photo color until the API serves car.photoUrl
        Box(Modifier.fillMaxWidth().height(width * 0.62f).background(AcidTheme.colors.photo).padding(8.dp)) { chip() }
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

/** A wave that slowly swells and settles: lime in dark mode, ink in light mode where lime barely shows. */
@Composable
private fun SwayingWave(sway: Boolean, modifier: Modifier = Modifier) {
    val amplitude = if (sway) {
        rememberInfiniteTransition().animateFloat(4f, 9f, infiniteRepeatable(tween(2400), RepeatMode.Reverse)).value
    } else 6f
    val color = if (AcidTheme.colors.isDark) Acid else Ink
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        drawWave(color, amplitude = amplitude.dp, wavelength = 60.dp, strokeWidth = 3.dp)
    }
}

/** 4.25 becomes "4.3"; Kotlin common has no String.format. */
private fun oneDecimal(x: Double): String {
    val tenths = (x * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
