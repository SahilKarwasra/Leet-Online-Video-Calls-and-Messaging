package com.laarasoft.frontend.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laarasoft.frontend.core.theme.LeetTheme
import com.laarasoft.frontend.core.utils.ObserveAsEvents
import com.laarasoft.frontend.core.utils.plainTextClipEntry
import com.laarasoft.frontend.domain.models.Participant
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random
import com.laarasoft.frontend.presentation.componenet.RequestMediaPermissions
import com.laarasoft.frontend.presentation.componenet.VideoContent
import com.laarasoft.frontend.core.utils.ui.UiEvent
import com.laarasoft.frontend.core.utils.ui.UiEventController
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val ReactionEmojis = listOf("👍", "👏", "❤️", "😂", "🎉", "😮")

private val WideBreakpoint = 840.dp
@Composable
fun MeetRoot(
    meetCode: String,
    viewModel: MeetViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is MeetEvents.NavigateBack -> onNavigateBack()
        }
    }

    RequestMediaPermissions(onFinished = { viewModel.onAction(MeetAction.StartCall) })

    MeetScreen(
        state = state,
        meetCode = meetCode,
        onAction = viewModel::onAction,
        onNavigateBack = { viewModel.onAction(MeetAction.LeaveMeeting) },
        videoContent = { participant, modifier ->
            state.videoTrackFor(participant)?.let { track ->
                VideoContent(track = track, mirror = participant.isLocal, modifier = modifier)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetScreen(
    state: MeetState,
    onAction: (MeetAction) -> Unit,
    meetCode: String = "",
    onNavigateBack: () -> Unit = {},
    videoContent: @Composable (Participant, Modifier) -> Unit = { _, _ -> }
) {
    var showReactions by remember { mutableStateOf(false) }
    var showMeetingReady by remember { mutableStateOf(true) }
    val copyMeetCode = rememberCopyMeetCode(meetCode)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        val isWide = maxWidth >= WideBreakpoint
        val outerPadding = if (isWide) 16.dp else 8.dp

        Row(
            Modifier
                .fillMaxSize()
                .padding(outerPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(Modifier.fillMaxSize()) {
                    Header(
                        elapsed = state.elapsed,
                        count = state.participants.size,
                        meetCode = meetCode
                    )
                    ParticipantGrid(
                        participants = state.participants,
                        maxTiles = if (isWide) 15 else 8,
                        videoContent = videoContent,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    ControlBar(
                        state = state,
                        isWide = isWide,
                        reactionsOpen = showReactions,
                        onToggleReactions = { showReactions = !showReactions },
                        onInfo = {
                            showMeetingReady = false
                            onAction(MeetAction.TogglePanel(MeetPanel.Info))
                        },
                        onAction = onAction,
                        onLeave = onNavigateBack,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                ReactionLayer(
                    reactions = state.reactions,
                    onDone = { onAction(MeetAction.DismissReaction(it)) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 72.dp)
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = showMeetingReady && meetCode.isNotBlank(),
                    enter = fadeIn() + slideInVertically { it / 3 },
                    exit = fadeOut() + slideOutVertically { it / 3 },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 72.dp)
                ) {
                    MeetingReadyCard(
                        meetCode = meetCode,
                        onCopy = copyMeetCode,
                        onDismiss = { showMeetingReady = false }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showReactions,
                    enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp)
                ) {
                    ReactionPicker(
                        handRaised = state.isHandRaised,
                        onReact = {
                            onAction(MeetAction.SendReaction(it))
                            showReactions = false
                        },
                        onToggleHand = {
                            onAction(MeetAction.ToggleHand)
                            showReactions = false
                        }
                    )
                }
            }

            if (isWide) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.panel != MeetPanel.None,
                    enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
                ) {
                    PanelContent(
                        state = state,
                        meetCode = meetCode,
                        onCopyMeetCode = copyMeetCode,
                        onAction = onAction,
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    )
                }
            }
        }

        if (!isWide && state.panel != MeetPanel.None) {
            ModalBottomSheet(
                onDismissRequest = { onAction(MeetAction.ClosePanel) },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                dragHandle = null
            ) {
                PanelContent(
                    state = state,
                    meetCode = meetCode,
                    onCopyMeetCode = copyMeetCode,
                    onAction = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .imePadding()
                )
            }
        }
    }
}
@Composable
private fun Header(elapsed: String, count: Int, meetCode: String) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(elapsed, color = mutedColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (meetCode.isNotBlank()) {
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .size(width = 1.dp, height = 14.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Text(
                meetCode,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Rounded.People, null, tint = mutedColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("$count", color = mutedColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
@Composable
private fun ParticipantGrid(
    participants: List<Participant>,
    maxTiles: Int,
    videoContent: @Composable (Participant, Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasOverflow = participants.size > maxTiles
    val visible = if (hasOverflow) participants.take(maxTiles - 1) else participants
    val hidden = participants.size - visible.size
    val count = visible.size + if (hasOverflow) 1 else 0
    if (count == 0) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Waiting for participants...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    BoxWithConstraints(modifier) {
        val cols = gridColumns(count, landscape = maxWidth > maxHeight)
        val rows = ceil(count / cols.toFloat()).toInt()
        val gap = if (count > 1) 8.dp else 0.dp

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(rows) { r ->
                val inRow = min(cols, count - r * cols)
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    repeat(inRow) { c ->
                        val index = r * cols + c
                        val tileModifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                        if (index < visible.size) {
                            ParticipantTile(visible[index], videoContent, tileModifier)
                        } else {
                            OverflowTile(hidden, tileModifier)
                        }
                    }
                }
            }
        }
    }
}

private fun gridColumns(count: Int, landscape: Boolean): Int = when {
    count <= 1 -> 1
    count == 2 -> if (landscape) 2 else 1
    count <= 4 -> 2
    count <= 6 -> if (landscape) 3 else 2
    count <= 9 -> 3
    else -> if (landscape) 4 else 3
}

@Composable
private fun ParticipantTile(
    participant: Participant,
    videoContent: @Composable (Participant, Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val speakingBorder = MaterialTheme.colorScheme.primary
    val border by animateColorAsState(if (participant.isSpeaking) speakingBorder else Color.Transparent)

    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(2.dp, border, shape)
    ) {
        if (participant.isVideoOn) {
            videoContent(participant, Modifier.fillMaxSize())
        } else {
            val size = (min(maxWidth.value, maxHeight.value) * 0.4f).coerceIn(40f, 112f).dp
            Avatar(
                name = participant.name,
                size = size,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // name + mic state
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (participant.isMuted) {
                Icon(
                    Icons.Rounded.MicOff,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (participant.isLocal) "You" else participant.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp)
            )
        }

        if (participant.isHandRaised) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PanTool,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun OverflowTile(hidden: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "+$hidden",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Avatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary,
        MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary,
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val (bgColor, textColor) = palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials(name),
            color = textColor,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun initials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

private enum class Tone { Neutral, Active, Off, Leave }

@Composable
private fun ControlBar(
    state: MeetState,
    isWide: Boolean,
    reactionsOpen: Boolean,
    onToggleReactions: () -> Unit,
    onInfo: () -> Unit,
    onAction: (MeetAction) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                icon = if (state.isMicOn) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                description = "Microphone",
                tone = if (state.isMicOn) Tone.Neutral else Tone.Off,
                onClick = { onAction(MeetAction.ToggleMic) }
            )
            ControlButton(
                icon = if (state.isCameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                description = "Camera",
                tone = if (state.isCameraOn) Tone.Neutral else Tone.Off,
                onClick = { onAction(MeetAction.ToggleCamera) }
            )
            if (isWide) {
                ControlButton(
                    icon = if (state.isScreenSharing) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                    description = "Share screen",
                    tone = if (state.isScreenSharing) Tone.Active else Tone.Neutral,
                    onClick = { onAction(MeetAction.ToggleScreenShare) }
                )
            }
            ControlButton(
                icon = Icons.Rounded.EmojiEmotions,
                description = "Reactions",
                tone = if (reactionsOpen || state.isHandRaised) Tone.Active else Tone.Neutral,
                onClick = onToggleReactions
            )
            ControlButton(
                icon = Icons.Rounded.Chat,
                description = "Chat",
                tone = if (state.panel == MeetPanel.Chat) Tone.Active else Tone.Neutral,
                badge = state.unreadCount > 0 && state.panel != MeetPanel.Chat,
                onClick = { onAction(MeetAction.TogglePanel(MeetPanel.Chat)) }
            )
            if (isWide) {
                ControlButton(
                    icon = Icons.Rounded.People,
                    description = "Participants",
                    tone = if (state.panel == MeetPanel.People) Tone.Active else Tone.Neutral,
                    onClick = { onAction(MeetAction.TogglePanel(MeetPanel.People)) }
                )
            }
            ControlButton(
                icon = Icons.Rounded.Info,
                description = "Meeting details",
                tone = if (state.panel == MeetPanel.Info) Tone.Active else Tone.Neutral,
                onClick = onInfo
            )
            ControlButton(
                icon = Icons.Rounded.CallEnd,
                description = "Leave",
                tone = Tone.Leave,
                width = 64.dp,
                onClick = onLeave
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    tone: Tone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
    badge: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val (container, tint) = when (tone) {
        Tone.Neutral -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
        Tone.Active -> colorScheme.primary to colorScheme.onPrimary
        Tone.Off -> colorScheme.errorContainer to colorScheme.onErrorContainer
        Tone.Leave -> colorScheme.error to colorScheme.onError
    }
    val bg by animateColorAsState(container)

    Box(
        modifier
            .size(width = width, height = 48.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary)
                    .border(1.5.dp, colorScheme.surfaceVariant, CircleShape)
            )
        }
    }
}

@Composable
private fun ReactionPicker(
    handRaised: Boolean,
    onReact: (String) -> Unit,
    onToggleHand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionEmojis.forEach { emoji ->
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onReact(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = 1.dp, height = 22.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (handRaised) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(onClick = onToggleHand),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PanTool,
                    "Raise hand",
                    tint = if (handRaised) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ReactionLayer(
    reactions: List<FloatingReaction>,
    onDone: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        reactions.forEach { reaction ->
            key(reaction.id) { FloatingEmoji(reaction, onDone) }
        }
    }
}

@Composable
private fun BoxScope.FloatingEmoji(reaction: FloatingReaction, onDone: (Long) -> Unit) {
    val progress = remember { Animatable(0f) }
    val drift = remember { (Random.nextFloat() - 0.5f) * 120f }
    val startX = remember { (Random.nextFloat() - 0.5f) * 220f }

    LaunchedEffect(reaction.id) {
        progress.animateTo(1f, tween(2800, easing = LinearOutSlowInEasing))
        onDone(reaction.id)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .graphicsLayer {
                val p = progress.value
                translationY = -p * 380.dp.toPx()
                translationX = (startX + drift * p).dp.toPx()
                alpha = 1f - p * p * p
                val s = 0.6f + 0.4f * min(1f, p * 5f)
                scaleX = s
                scaleY = s
            }
    ) {
        Text(text = reaction.emoji, fontSize = 34.sp)
        Text(
            text = reaction.sender,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
@Composable
private fun PanelContent(
    state: MeetState,
    meetCode: String,
    onCopyMeetCode: () -> Unit,
    onAction: (MeetAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelTab(
                icon = Icons.Rounded.Chat,
                selected = state.panel == MeetPanel.Chat,
                onClick = { onAction(MeetAction.OpenPanel(MeetPanel.Chat)) }
            )
            PanelTab(
                icon = Icons.Rounded.People,
                label = state.participants.size.toString(),
                selected = state.panel == MeetPanel.People,
                onClick = { onAction(MeetAction.OpenPanel(MeetPanel.People)) }
            )
            PanelTab(
                icon = Icons.Rounded.Info,
                selected = state.panel == MeetPanel.Info,
                onClick = { onAction(MeetAction.OpenPanel(MeetPanel.Info)) }
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onAction(MeetAction.ClosePanel) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        when (state.panel) {
            MeetPanel.Chat -> ChatPanel(
                messages = state.messages,
                onSend = { onAction(MeetAction.SendMessage(it)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            MeetPanel.People -> PeoplePanel(
                participants = state.participants,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            MeetPanel.Info -> MeetingDetailsPanel(
                meetCode = meetCode,
                onCopy = onCopyMeetCode,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            MeetPanel.None -> Unit
        }
    }
}

@Composable
private fun rememberCopyMeetCode(meetCode: String): () -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(meetCode, clipboard, scope) {
        {
            scope.launch {
                clipboard.setClipEntry(plainTextClipEntry(meetCode))
                UiEventController.send(UiEvent.Snackbar("Meeting code copied"))
            }
        }
    }
}

@Composable
private fun MeetingReadyCard(
    meetCode: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier.widthIn(max = 360.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Your meeting's ready",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                "Share this meeting code with others you want in the meeting",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(end = 12.dp, top = 4.dp, bottom = 16.dp)
            )
            MeetCodeRow(meetCode, onCopy, Modifier.padding(end = 12.dp))
        }
    }
}

@Composable
private fun MeetCodeRow(meetCode: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            meetCode,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onCopy),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                "Copy meeting code",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MeetingDetailsPanel(meetCode: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            "Meeting details",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            "Joining info",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            meetCode,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        Row(
            Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onCopy)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Copy joining info",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PanelTab(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    label: String? = null
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val bgColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent

    Row(
        Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ChatPanel(
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(message)
            }
        }
        ChatInput(onSend, Modifier.padding(12.dp))
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isMine = message.isMine
    val bubbleBg = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (!isMine) {
            Text(
                message.sender,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }
        Text(
            text = message.text,
            color = textColor,
            fontSize = 14.sp,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bubbleBg)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun ChatInput(onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    fun send() {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            onSend(trimmed)
            text = ""
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        "Message",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 15.sp
                    )
                }
                inner()
            }
        )
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (text.isBlank()) Color.Transparent else MaterialTheme.colorScheme.primary)
                .clickable(enabled = text.isNotBlank()) { send() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Send,
                "Send",
                tint = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PeoplePanel(participants: List<Participant>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(participants, key = { it.id }) { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(p.name, 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (p.isLocal) "${p.name} (You)" else p.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (p.isHandRaised) {
                    Icon(
                        Icons.Rounded.PanTool,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Icon(
                    if (p.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    null,
                    tint = if (p.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    LeetTheme {
        MeetScreen(
            meetCode = "abcd1234",
            state = MeetState(
                elapsed = "12:48",
                participants = listOf(
                    Participant("1", "You", isLocal = true, isVideoOn = false),
                    Participant("2", "Aisha Khan", isSpeaking = true),
                    Participant("3", "Marco Rossi", isMuted = true),
                    Participant("4", "Lena Fischer", isHandRaised = true),
                    Participant("5", "Ravi Patel"),
                    Participant("6", "Sofia Garcia", isMuted = true),
                ),
                messages = listOf(
                    ChatMessage("a", "Aisha Khan", "Can everyone see my screen?", false),
                    ChatMessage("b", "You", "Yep, all good 👍", true),
                ),
            ),
            onAction = {}
        )
    }
}