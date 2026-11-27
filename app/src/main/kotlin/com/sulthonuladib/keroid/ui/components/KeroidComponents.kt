package com.sulthonuladib.keroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sulthonuladib.keroid.ui.theme.KeroidMono
import com.sulthonuladib.keroid.ui.theme.keroid
import kotlin.math.cos

/** 96 dp screen header: a 32/40 title, a quiet subtitle and 48 dp actions. */
@Composable
fun LargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    titleTag: String? = null,
    subtitle: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 96.dp).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (titleTag != null) Modifier.testTag(titleTag) else Modifier,
            )
            Box(Modifier.padding(top = 2.dp)) { subtitle() }
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** 64 dp detail bar with back, a 20/26 title and an 11.5 subtitle. */
@Composable
fun DetailBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** Rounded pill used for filters and either/or choices. */
@Composable
fun SegmentedGroup(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    counts: List<Int?> = List(options.size) { null },
) {
    val colors = MaterialTheme.keroid
    val showCounts = LocalDensity.current.fontScale <= 1.3f
    Row(
        modifier.fillMaxWidth().height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.raised)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val on = index == selected
            Box(
                Modifier.weight(1f).height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (showCounts) counts.getOrNull(index)?.let { count ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            count.toString(),
                            fontFamily = KeroidMono,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = (if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                .copy(alpha = 0.58f),
                        )
                    }
                }
            }
        }
    }
}

/** One grouped-list container: a window surface with hairline row separators. */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.keroid.window),
    ) { content() }
}

/** Hairline between rows inside a grouped card. */
@Composable
fun GroupDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.keroid.hairline))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 4.dp),
        fontFamily = KeroidMono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Project header: chevron, 22/28 name, mono count and an add action. */
@Composable
fun ProjectHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.ChevronRight,
            null,
            modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        CountBadge(count)
        if (onAdd != null) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAdd, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Add, "New session in $name", modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.onSurface,
        contentColor = MaterialTheme.keroid.sunken,
    ) {
        Text(
            count.toString(),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            fontFamily = KeroidMono,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Amber "Needs you" pill, primary "Working" text, muted idle. */
@Composable
fun SessionStatusLabel(status: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.keroid
    when (status) {
        "waiting", "attention" -> Surface(
            modifier,
            shape = RoundedCornerShape(10.dp),
            color = colors.attention,
            contentColor = colors.onAttention,
        ) {
            Text(
                "Needs you",
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                fontFamily = KeroidMono,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        "error" -> Text(
            "Error",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = colors.danger,
        )
        "working" -> Text(
            "Working",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        "exited" -> Text(
            "Exited",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Text(
            "Idle",
            modifier = modifier,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Monospace agent chip, magenta for codex, cyan for claude. */
@Composable
fun AgentBadge(agent: String, modifier: Modifier = Modifier) {
    if (agent.isBlank()) return
    val colors = MaterialTheme.keroid
    val lower = agent.lowercase()
    val container = when {
        "codex" in lower -> colors.agentCodexContainer
        "claude" in lower -> colors.agentClaudeContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        "codex" in lower -> colors.agentCodex
        "claude" in lower -> colors.agentClaude
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier, shape = RoundedCornerShape(5.dp), color = container, contentColor = content) {
        Text(
            agent.take(10),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            fontFamily = KeroidMono,
            fontSize = 8.5.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun StatusDot(connected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(8.dp).clip(CircleShape)
            .background(if (connected) MaterialTheme.keroid.success else MaterialTheme.keroid.quiet),
    )
}

/** 56 dp square-ish FAB with a 16 dp corner. */
@Composable
fun AddFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Add",
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 8.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, contentDescription, modifier = Modifier.size(26.dp))
        }
    }
}

/** A row that opens a detail screen: icon, two lines and a chevron. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(22.dp),
            tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(
            Icons.Outlined.ChevronRight,
            null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The design's six-lobed machine tile. */
val CookieShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width, size.height) / 2f
        val path = Path()
        val steps = 120
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * 2f * Math.PI.toFloat()
            val r = radius * (1f + 0.05f * cos(6f * theta))
            val x = cx + r * cos(theta)
            val y = cy + r * kotlin.math.sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** A machine avatar using [CookieShape]. */
@Composable
fun CookieTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    connected: Boolean = true,
) {
    val colors = MaterialTheme.keroid
    Box(modifier.size(56.dp).clip(CookieShape).background(if (selected) MaterialTheme.colorScheme.primary else colors.raised)) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Text(
                label.take(2).lowercase(),
                fontFamily = KeroidMono,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 5.dp).size(9.dp)
                .clip(CircleShape)
                .background(if (connected) colors.success else colors.quiet),
        )
    }
}

/** Small divider for key bars. */
@Composable
fun VerticalHairline(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).height(24.dp).background(MaterialTheme.keroid.hairline))
}
