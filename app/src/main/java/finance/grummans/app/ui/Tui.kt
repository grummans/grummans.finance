package finance.grummans.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import finance.grummans.app.R
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Gruvbox Material Dark, taken from the Figma file (DESIGN notes in UX_SPEC).
val Charcoal = Color(0xFF1D2021)
val Panel = Color(0xFF282828)
val Raised = Color(0xFF32302F)
val Border = Color(0xFF504945)
val Cream = Color(0xFFD4BE98)
val Muted = Color(0xFFA89984)
val Yellow = Color(0xFFD8A657)
val Green = Color(0xFFA9B665)
val Red = Color(0xFFEA6962)
val Olive = Color(0xFF7C6F64)

@Composable
fun tuiFont() = FontFamily(
    Font(R.font.roboto_mono_regular, FontWeight.Normal),
    Font(R.font.roboto_mono_medium, FontWeight.Medium),
    Font(R.font.roboto_mono_bold, FontWeight.Bold),
)

@Composable
fun tuiStyle(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Cream) =
    TextStyle(fontFamily = tuiFont(), fontSize = size.sp, fontWeight = weight, color = color)

private val VND = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
private val DAY_GROUP = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private val DAY_SHORT = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val BUSINESS_DATE = ZoneId.of("Asia/Ho_Chi_Minh")

/** `3.700.000₫` — dot grouping, no space before the currency sign. */
fun money(amount: Long): String = "${VND.format(amount)}₫"

// An amount is read as typed: a digit filter would turn "-100" into 100 and a
// number beyond Long into 0, so an invalid entry has to stay invalid. Only whole
// VND are accepted, written plainly or with dot grouping as the fields show them.
private val AMOUNT_INPUT = Regex("""^\d+(?:\.\d{3})*$""")

fun amountOrNull(text: String): Long? {
    val trimmed = text.trim()
    if (!AMOUNT_INPUT.matches(trimmed)) return null
    return trimmed.replace(".", "").toLongOrNull()
}

fun signedMoney(amount: Long, outgoing: Boolean): String = (if (outgoing) "-" else "+") + money(amount)

/** `1.200.000` for input fields. */
fun plainAmount(amount: Long): String = VND.format(amount)

fun OffsetDateTime.business(): OffsetDateTime = atZoneSameInstant(BUSINESS_DATE).toOffsetDateTime()

fun stamp(iso: String, pattern: String): String = runCatching {
    val value = OffsetDateTime.parse(iso).business()
    when (pattern) {
        "day" -> value.format(DAY_GROUP).uppercase(Locale.ENGLISH)
        "short" -> value.format(DAY_SHORT).uppercase(Locale.ENGLISH)
        "log" -> value.format(DAY_SHORT).replace(" ", "").uppercase(Locale.ENGLISH) + " " + value.format(CLOCK)
        else -> value.format(CLOCK)
    }
}.getOrDefault(iso.take(16))

fun dateOnly(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).business().toLocalDate().toString()
}.getOrDefault(iso.take(10))

// ---------------------------------------------------------------- building blocks

@Composable
fun TuiTopBar(path: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(path, style = tuiStyle(15, color = Cream))
        Text("NORMAL", style = tuiStyle(11, color = Muted))
    }
}

@Composable
fun TuiStatusBar(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("-- NORMAL --", style = tuiStyle(11, color = Muted))
        Text("UTF-8 · SYNCED", style = tuiStyle(11, color = Muted))
    }
}

@Composable
fun TuiPanel(
    title: String,
    modifier: Modifier = Modifier,
    borderColor: Color = Border,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("[ $title ]", style = tuiStyle(12, FontWeight.Medium, Yellow))
        content()
    }
}

@Composable
fun TuiCard(modifier: Modifier = Modifier, borderColor: Color = Border, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Raised, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** `label .......... value` row; the value keeps its colour meaning (green/red/yellow). */
@Composable
fun TuiRow(label: String, value: String, valueColor: Color = Cream, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = tuiStyle(13, color = Muted))
        Text(value, style = tuiStyle(13, FontWeight.Medium, valueColor))
    }
}

@Composable
fun TuiInput(label: String, value: String, onValueChange: (String) -> Unit, helper: String, numeric: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TuiCard {
            Text(label.uppercase(Locale.ENGLISH), style = tuiStyle(11, color = Muted))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = tuiStyle(20, FontWeight.Bold, Cream),
                cursorBrush = SolidColor(Yellow),
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(helper, style = tuiStyle(13, color = Muted.copy(alpha = 0.7f)))
                        inner()
                    }
                },
            )
            Text(helper, style = tuiStyle(11, color = Muted))
        }
    }
}

/** `[██████░░░░] 68%` */
@Composable
fun TuiBar(fraction: Float, modifier: Modifier = Modifier) {
    val clamped = fraction.coerceIn(0f, 1f)
    val slots = 10
    val filled = Math.round(clamped * slots)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row {
            Text("[", style = tuiStyle(13, color = Muted))
            Text("█".repeat(filled), style = tuiStyle(13, color = Green))
            Text("░".repeat(slots - filled), style = tuiStyle(13, color = Olive))
            Text("]", style = tuiStyle(13, color = Muted))
        }
        Text("${Math.round(clamped * 100)}%", style = tuiStyle(12, color = Muted))
    }
}

@Composable
fun TuiButton(text: String, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (primary && enabled) Yellow else Color.Transparent, RoundedCornerShape(6.dp))
            .border(1.dp, if (enabled) (if (primary) Yellow else Border) else Border, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "[ ${text.uppercase(Locale.ENGLISH)} ]",
            style = tuiStyle(
                13,
                FontWeight.Medium,
                when {
                    !enabled -> Muted.copy(alpha = 0.5f)
                    primary -> Charcoal
                    else -> Cream
                },
            ),
        )
    }
}

@Composable
fun TuiGreenButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (enabled) Green else Raised, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("[ ${text.uppercase(Locale.ENGLISH)} ]", style = tuiStyle(13, FontWeight.Medium, if (enabled) Charcoal else Muted))
    }
}

/** `[ TIMELINE ] [ LOG ]` sub-tab strip. */
@Composable
fun TuiTabs(tabs: List<String>, active: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEach { tab ->
            Text(
                if (tab == active) "[ $tab ]" else "[ $tab ]",
                style = tuiStyle(12, if (tab == active) FontWeight.Bold else FontWeight.Normal,
                    if (tab == active) Green else Muted),
                modifier = Modifier.clickable { onSelect(tab) }.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
fun TuiBottomNav(active: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .border(1.dp, Border)
            .height(49.dp),
    ) {
        listOf("HOME", "TXNS", "BILLS", "BUDGET").forEach { label ->
            NavCell(label, label == active) { onSelect(label) }
        }
    }
}

@Composable
private fun RowScope.NavCell(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(49.dp)
            .background(if (active) Green else Panel)
            .border(1.dp, Border)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = tuiStyle(12, FontWeight.Medium, if (active) Charcoal else Cream))
    }
}

/** Dialog surface in the same grammar: bordered panel on a dimmed background. */
@Composable
fun TuiDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(10.dp))
                .border(1.dp, Border, RoundedCornerShape(10.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("[ $title ]", style = tuiStyle(12, FontWeight.Medium, Yellow))
            content()
        }
    }
}

/** One tappable option row inside a dialog or list. */
@Composable
fun TuiOption(label: String, value: String? = null, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Green.copy(alpha = 0.16f) else Raised, RoundedCornerShape(6.dp))
            .border(1.dp, if (selected) Green.copy(alpha = 0.6f) else Border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = tuiStyle(13, color = Cream))
        if (value != null) Text(value, style = tuiStyle(12, color = Muted))
    }
}

@Composable
fun TuiEmpty(text: String) {
    Text(text, style = tuiStyle(13, color = Muted), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
}

@Suppress("unused")
private val unusedBorderStroke = BorderStroke(1.dp, Border)
