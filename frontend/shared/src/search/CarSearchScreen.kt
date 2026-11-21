package com.avans.rentacar.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avans.rentacar.api.Api
import com.avans.rentacar.api.ApiError
import com.avans.rentacar.cars.BodyStyles
import com.avans.rentacar.cars.Car
import com.avans.rentacar.cars.CarKind
import com.avans.rentacar.cars.CarQuery
import com.avans.rentacar.cars.Daypart
import com.avans.rentacar.cars.FuelTypes
import com.avans.rentacar.cars.Sort
import com.avans.rentacar.cars.Transmissions
import com.avans.rentacar.cars.summary
import com.avans.rentacar.components.AcidChip
import com.avans.rentacar.components.AcidTextField
import com.avans.rentacar.components.OutlineCard
import com.avans.rentacar.components.PrimaryButton
import com.avans.rentacar.components.SecondaryButton
import com.avans.rentacar.theme.Acid
import com.avans.rentacar.theme.AcidShapes
import com.avans.rentacar.theme.AcidTheme
import com.avans.rentacar.theme.Ink
import com.avans.rentacar.theme.Pink
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt
import kotlin.time.Clock

// Below this content width the filters fold behind a button; above it they get their own column.
private val FilterColumnBreakpoint = 720.dp

/**
 * Page 4, car search: everyone can use it. "Free on" a date and daypart, filters, sort, and more cars
 * while scrolling. [onBack] adds a back button (to Welcome for a visitor).
 */
@Composable
fun CarSearchScreen(api: Api, onCar: (Int) -> Unit, onBack: (() -> Unit)? = null) {
    var query by remember { mutableStateOf(CarQuery()) }
    var cars by remember { mutableStateOf(emptyList<Car>()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var filtersOpen by remember { mutableStateOf(false) }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val grid = rememberLazyGridState()

    LaunchedEffect(query, attempt) {
        loading = true
        error = null
        cars = emptyList()
        try {
            val page = api.cars(query)
            cars = page.items
            total = page.total
        } catch (e: ApiError) {
            error = e
        }
        loading = false
    }

    // The next page when the last cards come into view. Keyed on the query so a new search cancels it.
    val nearEnd by remember { derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= grid.layoutInfo.totalItemsCount - 4 } == true } }
    LaunchedEffect(nearEnd, cars.size, total, query) {
        if (nearEnd && !loading && error == null && cars.size in 1 until total) {
            loading = true
            try {
                cars = cars + api.cars(query, offset = cars.size).items
            } catch (e: ApiError) {
                error = e
            }
            loading = false
        }
    }

    val retry: () -> Unit = { attempt++ }
    BoxWithConstraints(Modifier.fillMaxSize().background(AcidTheme.colors.bg)) {
        val wide = maxWidth >= FilterColumnBreakpoint
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Column(
                    Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) { Filters(query) { query = it } }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(240.dp),
                state = grid,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                full { Header(onBack) }
                full { FreeOn(query, today) { query = it } }
                full { CountLine(query, today, cars.size, total, loading, error != null, wide, filtersOpen) { filtersOpen = !filtersOpen } }
                if (!wide && filtersOpen) full {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { Filters(query) { query = it } }
                }
                error?.let { e -> full { ErrorBanner(e, api, retry) } }
                when {
                    loading && cars.isEmpty() -> items(4) { SkeletonCard() }
                    !loading && error == null && cars.isEmpty() -> full { Empty(query) { query = query.cleared() } }
                    else -> items(cars, key = { it.id }) { SearchCarCard(it) { onCar(it.id) } }
                }
                if (loading && cars.isNotEmpty()) full {
                    Text("loading more cars…", style = AcidTheme.type.mono, color = AcidTheme.colors.muted)
                }
            }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Header(onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("find a car", style = AcidTheme.type.headline, modifier = Modifier.weight(1f))
        if (onBack != null) SecondaryButton("Back", onBack)
    }
}

/** "free on": any day, or one of the coming seven days with a daypart. */
@Composable
private fun FreeOn(query: CarQuery, today: LocalDate, onChange: (CarQuery) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("free on", style = AcidTheme.type.mono, color = AcidTheme.colors.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcidChip("any day", query.date == null, { onChange(query.copy(date = null)) })
            for (offset in 0..6) {
                val date = today.plus(offset, DateTimeUnit.DAY)
                AcidChip(dayLabel(date, today), query.date == date, { onChange(query.copy(date = date)) })
            }
        }
        if (query.date != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (part in Daypart.entries) AcidChip(part.label, query.daypart == part, { onChange(query.copy(daypart = part)) })
            }
        }
    }
}

/** "today", "tomorrow", then "wed 25". */
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "today"
    today.plus(1, DateTimeUnit.DAY) -> "tomorrow"
    else -> "${date.dayOfWeek.name.take(3).lowercase()} ${date.day}"
}

@Composable
private fun CountLine(query: CarQuery, today: LocalDate, shown: Int, total: Int, loading: Boolean, failed: Boolean, wide: Boolean, filtersOpen: Boolean, onToggleFilters: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val text = when {
            loading && shown == 0 -> "looking for cars…"
            failed && shown == 0 -> "" // the error says what happened
            else -> countText(query, today, shown, total)
        }
        Text(text, style = AcidTheme.type.mono, color = AcidTheme.colors.muted, modifier = Modifier.weight(1f))
        if (!wide) {
            val n = query.filterCount
            AcidChip(if (n == 0) "filters" else "filters ($n)", filtersOpen || n > 0, onToggleFilters)
        }
    }
}

/** "12 cars free today, morning", or "showing 20 of 134 cars" while not all are loaded. */
fun countText(query: CarQuery, today: LocalDate, shown: Int, total: Int): String {
    val cars = if (total == 1) "1 car" else "$total cars"
    val count = if (shown in 1 until total) "showing $shown of $cars" else cars
    val day = query.date ?: return count
    val on = dayLabel(day, today).let { if (it == "today" || it == "tomorrow") it else "on $it" }
    return "$count free $on, ${query.daypart.label}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(query: CarQuery, onChange: (CarQuery) -> Unit) {
    FilterGroup("kind") {
        for (kind in CarKind.entries) AcidChip(kind.label, query.kind == kind, { onChange(query.withKind(if (query.kind == kind) null else kind)) })
    }
    FilterGroup("body style") {
        for (style in BodyStyles) AcidChip(style.lowercase(), query.bodyStyle == style, {
            onChange(query.copy(bodyStyle = if (query.bodyStyle == style) null else style))
        })
    }
    FilterGroup("gearbox") {
        for (t in Transmissions) AcidChip(t.lowercase(), query.transmission == t, {
            onChange(query.copy(transmission = if (query.transmission == t) null else t))
        })
    }
    if (query.kind == CarKind.Combustion) FilterGroup("fuel") {
        for (fuel in FuelTypes) AcidChip(fuel.lowercase(), query.fuelType == fuel, {
            onChange(query.copy(fuelType = if (query.fuelType == fuel) null else fuel))
        })
    }
    FilterGroup("sort by") {
        for (sort in Sort.entries) AcidChip(sort.label, query.sort == sort, { onChange(query.copy(sort = sort)) })
    }
    if (query.filterCount > 0) SecondaryButton("Clear filters", { onChange(query.copy(kind = null, bodyStyle = null, transmission = null, fuelType = null)) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(label: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = AcidTheme.type.mono, color = AcidTheme.colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
private fun SearchCarCard(car: Car, onClick: () -> Unit) {
    OutlineCard(Modifier.fillMaxWidth(), onClick = onClick) {
        // shortcut: a block in the photo color until the car list sends a photo
        Box(Modifier.fillMaxWidth().height(140.dp).background(AcidTheme.colors.photo).padding(10.dp), contentAlignment = Alignment.BottomStart) {
            Badge("€${car.rate.roundToInt()}", Acid)
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(car.brand.lowercase(), style = AcidTheme.type.title, maxLines = 1, modifier = Modifier.weight(1f))
                car.averageRating?.let { Badge("${(it * 10).roundToInt() / 10.0} ★", Pink) }
            }
            Text(car.summary, style = AcidTheme.type.mono, color = AcidTheme.colors.muted, maxLines = 1)
        }
    }
}

// Dark text on lime or pink in both modes: lime is never text on a light background.
@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = AcidTheme.type.label, color = Ink, modifier = Modifier.background(color, AcidShapes.badge).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun SkeletonCard() {
    val photo = AcidTheme.colors.photo
    OutlineCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(140.dp).background(photo))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(18.dp).background(photo, AcidShapes.badge))
            Box(Modifier.fillMaxWidth(0.8f).height(12.dp).background(photo, AcidShapes.badge))
        }
    }
}

@Composable
private fun Empty(query: CarQuery, onClear: () -> Unit) {
    Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("No cars match these filters.", style = AcidTheme.type.body)
        if (query.filterCount > 0 || query.date != null) SecondaryButton("Clear filters", onClear)
    }
}

/**
 * The server's message with "Try again". When the server didn't answer at all, also its address,
 * so a phone can point at the computer that runs the API.
 */
@Composable
private fun ErrorBanner(error: ApiError, api: Api, onRetry: () -> Unit) {
    val c = AcidTheme.colors
    // shortcut: the address only lives until the app closes; keep it on the device if people need it more often
    var address by remember { mutableStateOf(api.baseUrl) }
    Column(
        Modifier.fillMaxWidth().border(2.dp, c.error, AcidShapes.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("! ${error.message}", style = AcidTheme.type.body, color = c.fg)
        if (error.unreachable) AcidTextField(address, { address = it }, "server address", placeholder = "http://192.168.1.20:8080")
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Try again", {
            api.baseUrl = address.trim().trimEnd('/')
            onRetry()
        })
    }
}
