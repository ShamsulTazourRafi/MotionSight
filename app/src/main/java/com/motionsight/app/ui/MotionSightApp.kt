@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.motionsight.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.motionsight.app.R
import com.motionsight.app.camera.CameraController
import com.motionsight.app.model.AppScreen
import com.motionsight.app.model.AppUiState
import com.motionsight.app.model.CameraStatus
import com.motionsight.app.model.MotionMode
import com.motionsight.app.model.Sheet
import com.motionsight.app.model.TargetPreset
import com.motionsight.app.render.MotionGlSurfaceView
import com.motionsight.app.ui.theme.MotionBlack
import com.motionsight.app.ui.theme.MotionGray
import com.motionsight.app.ui.theme.MotionMuted
import com.motionsight.app.ui.theme.MotionSurface
import com.motionsight.app.ui.theme.MotionSurfaceRaised
import com.motionsight.app.ui.theme.MotionWhite
import com.motionsight.app.ui.theme.WarningAmber
import com.motionsight.app.viewmodel.MotionViewModel
import kotlin.math.roundToInt
import java.util.Locale

private val Poppins = FontFamily(Font(R.font.poppins_regular, FontWeight.Normal))
private val ScreenPadding = 24.dp
private val CardShape = RoundedCornerShape(30.dp)
private val PillShape = RoundedCornerShape(999.dp)

@Composable
fun MotionSightApp(viewModel: MotionViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) { viewModel.onLaunch(permissionGranted) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.showSetup()
    }

    AnimatedContent(
        targetState = state.screen,
        label = "app screen",
        modifier = Modifier
            .fillMaxSize()
            .background(MotionBlack),
    ) { screen ->
        when (screen) {
            AppScreen.WELCOME -> WelcomeScreen(
                onContinue = viewModel::showPermission,
                onInfo = { viewModel.showInfo(AppScreen.WELCOME) },
            )
            AppScreen.PERMISSION -> PermissionScreen(
                denied = permissionDenied,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                },
                onBack = { permissionDenied = false; viewModel.showWelcome() },
            )
            AppScreen.SETUP -> SetupScreen(
                state = state,
                onMode = viewModel::setMode,
                onPreset = viewModel::setPreset,
                onStart = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.showCamera()
                    } else {
                        viewModel.showPermission()
                    }
                },
                onInfo = { viewModel.showInfo(AppScreen.SETUP) },
            )
            AppScreen.CAMERA -> CameraScreen(state = state, viewModel = viewModel)
            AppScreen.INFO -> InformationScreen(onBack = viewModel::closeInfo)
        }
    }
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit, onInfo: () -> Unit) {
    BrandedScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ScreenPadding, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Wordmark()
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                MotionPreviewIllustration()
                Text(
                    text = "SEE WHAT MOVES",
                    color = MotionWhite,
                    fontFamily = Poppins,
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                )
                Text(
                    text = "Motion Sight turns movement white against a dim live view. It highlights motion — it does not identify insects.",
                    color = MotionMuted,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(text = "CONTINUE", onClick = onContinue)
                TextButton(onClick = onInfo, modifier = Modifier.fillMaxWidth()) {
                    Text("HOW IT WORKS", color = MotionMuted, letterSpacing = 1.2.sp)
                }
            }
        }
    }
}

@Composable
private fun MotionPreviewIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(CardShape)
            .background(Color(0xFF070707)),
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .offset(x = 34.dp, y = 94.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(5.dp)
                .offset(x = 154.dp, y = 58.dp)
                .background(Color.White.copy(alpha = 0.92f), PillShape),
        )
        Box(
            modifier = Modifier
                .size(52.dp)
                .offset(x = 260.dp, y = 122.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White),
        )
    }
}

@Composable
private fun PermissionScreen(
    denied: Boolean,
    onRequest: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    BrandedScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Wordmark()
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MotionSurfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("◉", color = MotionWhite, fontSize = 42.sp)
                }
                Text(
                    text = if (denied) "CAMERA ACCESS IS REQUIRED" else "ALLOW THE LIVE VIEW",
                    color = MotionWhite,
                    fontFamily = Poppins,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                )
                Text(
                    text = "The camera supplies frames for motion analysis. Processing stays on this device, and Motion Sight never saves photos or video.",
                    color = MotionMuted,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = if (denied) "TRY AGAIN" else "ALLOW CAMERA ACCESS",
                    onClick = onRequest,
                )
                if (denied) {
                    TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("OPEN APP SETTINGS", color = MotionMuted, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    state: AppUiState,
    onMode: (MotionMode) -> Unit,
    onPreset: (TargetPreset) -> Unit,
    onStart: () -> Unit,
    onInfo: () -> Unit,
) {
    BrandedScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 20.dp),
        ) {
            Wordmark()
            Spacer(Modifier.height(34.dp))
            Text("SET UP THE VIEW", style = HeadingStyle)
            Spacer(Modifier.height(10.dp))
            Text(
                "Confirm how the phone will move, then choose the movement you want to reveal.",
                color = MotionMuted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(30.dp))
            SectionLabel("CAMERA")
            Spacer(Modifier.height(10.dp))
            SelectableCard(
                title = "Tripod",
                detail = "Recommended · camera stays fixed",
                selected = state.mode == MotionMode.FIXED,
                icon = R.drawable.ic_tripod,
                onClick = { onMode(MotionMode.FIXED) },
            )
            Spacer(Modifier.height(10.dp))
            SelectableCard(
                title = "Handheld",
                detail = "Experimental · move slowly and smoothly",
                selected = state.mode == MotionMode.HANDHELD,
                icon = R.drawable.ic_handheld,
                onClick = { onMode(MotionMode.HANDHELD) },
            )
            Spacer(Modifier.height(28.dp))
            SectionLabel("TARGET MOVEMENT")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactChoice(
                    title = "Crawling",
                    detail = "Slower · persistent",
                    selected = state.preset == TargetPreset.CRAWLING,
                    onClick = { onPreset(TargetPreset.CRAWLING) },
                    modifier = Modifier.weight(1f),
                )
                CompactChoice(
                    title = "Flying",
                    detail = "Brief · smaller",
                    selected = state.preset == TargetPreset.FLYING,
                    onClick = { onPreset(TargetPreset.FLYING) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(28.dp))
            Guidance(state.mode, state.preset)
            Spacer(Modifier.height(30.dp))
            PrimaryButton("START MOTION VIEW", onStart)
            TextButton(onClick = onInfo, modifier = Modifier.fillMaxWidth()) {
                Text("TIPS & LIMITATIONS", color = MotionMuted, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SelectableCard(
    title: String,
    detail: String,
    selected: Boolean,
    icon: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) MotionSurfaceRaised else MotionSurface)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.36f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (selected) MotionBlack else MotionSurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(31.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = MotionWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = MotionMuted, fontSize = 13.sp)
        }
        Text(if (selected) "●" else "○", color = if (selected) MotionWhite else MotionGray, fontSize = 18.sp)
    }
}

@Composable
private fun CompactChoice(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) MotionSurfaceRaised else MotionSurface)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = MotionWhite, fontSize = 17.sp)
        Text(detail, color = MotionMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Guidance(mode: MotionMode, preset: TargetPreset) {
    val text = when (mode to preset) {
        MotionMode.FIXED to TargetPreset.CRAWLING -> "Brace or mount the phone. A plain background and close focus give the cleanest result."
        MotionMode.FIXED to TargetPreset.FLYING -> "Use bright light and frame a small observation area. Short exposure improves fast movement."
        MotionMode.HANDHELD to TargetPreset.CRAWLING -> "Move slowly. Stabilization will reject frames whenever the scene cannot be aligned confidently."
        MotionMode.HANDHELD to TargetPreset.FLYING -> "Most experimental. Use strong light and very smooth movement for the best chance of a result."
        else -> "Keep the camera steady and use strong, even light."
    }
    Text(
        text,
        color = if (mode == MotionMode.HANDHELD) WarningAmber else MotionMuted,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(MotionSurface, RoundedCornerShape(20.dp))
            .padding(16.dp),
    )
}

@Composable
private fun CameraScreen(state: AppUiState, viewModel: MotionViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(state.metrics.resolutionFallback) { CameraController(context.applicationContext) }
    var glSurface by remember { mutableStateOf<MotionGlSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner, controller) {
        controller.bind(
            lifecycleOwner = lifecycleOwner,
            useFallbackResolution = state.metrics.resolutionFallback,
            analyzer = viewModel::analyze,
            onBound = { viewModel.onCameraBound(it.torchAvailable, it.minZoomRatio, it.maxZoomRatio) },
            onError = viewModel::onCameraError,
        )
        onDispose { controller.release() }
    }

    LaunchedEffect(state.torchEnabled, controller) { controller.setTorch(state.torchEnabled) }
    LaunchedEffect(state.zoomRatio, controller) { controller.setZoom(state.zoomRatio) }

    DisposableEffect(lifecycleOwner, glSurface) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> glSurface?.onPause()
                Lifecycle.Event.ON_RESUME -> {
                    glSurface?.onResume()
                    viewModel.resetDetection()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurface?.onPause()
        }
    }

    BackHandler { viewModel.showSetup() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MotionBlack)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        Column(Modifier.fillMaxSize()) {
            Wordmark(modifier = Modifier.height(54.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 13.dp, end = 13.dp, bottom = 21.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFF080808))
                    .combinedClickable(
                        onClick = { viewModel.showSheet(Sheet.CONTROLS) },
                        onLongClick = { viewModel.showSheet(Sheet.DIAGNOSTICS) },
                    ),
            ) {
                AndroidView(
                    factory = { MotionGlSurfaceView(it).also { view -> glSurface = view } },
                    update = { view -> state.frame?.let(view::submitFrame) },
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "Live motion view. White areas indicate movement." },
                )
                if (state.frame == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("WAITING FOR CAMERA", color = MotionGray, fontSize = 12.sp, letterSpacing = 1.3.sp)
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    QuietChip(
                        text = state.preset.name,
                        onClick = { viewModel.showSheet(Sheet.PROFILE) },
                    )
                    QuietChip(
                        text = if (state.metrics.fps > 0) "${state.metrics.fps.roundToInt()} FPS" else "DIAGNOSTICS",
                        onClick = { viewModel.showSheet(Sheet.DIAGNOSTICS) },
                    )
                }

                if (state.statusMessage != null) {
                    StatusBanner(
                        state = state,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp, start = 20.dp, end = 20.dp),
                    )
                }

                ModeCapsule(
                    mode = state.mode,
                    onMode = viewModel::setMode,
                    onOpenControls = { viewModel.showSheet(Sheet.CONTROLS) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 11.dp),
                )

                if (state.cameraStatus == CameraStatus.ERROR) {
                    ErrorOverlay(state.errorMessage, onBack = viewModel::showSetup)
                }
            }
        }

        when (state.sheet) {
            Sheet.CONTROLS -> ControlsSheet(state, viewModel, onDismiss = viewModel::closeSheet)
            Sheet.DIAGNOSTICS -> DiagnosticsSheet(state, onDismiss = viewModel::closeSheet)
            Sheet.PROFILE -> ProfileSheet(state, viewModel, onDismiss = viewModel::closeSheet)
            Sheet.NONE -> Unit
        }
    }
}

@Composable
private fun ModeCapsule(
    mode: MotionMode,
    onMode: (MotionMode) -> Unit,
    onOpenControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionOffset by animateDpAsState(
        targetValue = if (mode == MotionMode.HANDHELD) 0.dp else 47.dp,
        label = "mode selector",
    )
    Box(
        modifier = modifier
            .width(107.dp)
            .height(54.dp)
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.30f), PillShape)
            .combinedClickable(onClick = {}, onLongClick = onOpenControls)
            .padding(3.5.dp),
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(selectionOffset.roundToPx(), 0) }
                .size(47.dp)
                .clip(CircleShape)
                .background(MotionSurfaceRaised),
        )
        Row(Modifier.fillMaxSize()) {
            ModeButton(
                icon = R.drawable.ic_handheld,
                description = "Handheld mode",
                selected = mode == MotionMode.HANDHELD,
                onClick = { onMode(MotionMode.HANDHELD) },
            )
            Spacer(Modifier.width(1.dp))
            ModeButton(
                icon = R.drawable.ic_tripod,
                description = "Tripod mode",
                selected = mode == MotionMode.FIXED,
                onClick = { onMode(MotionMode.FIXED) },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ModeButton(
    icon: Int,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$description${if (selected) ", selected" else ""}" },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}

@Composable
private fun QuietChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.72f),
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(PillShape)
            .background(Color.Black.copy(alpha = 0.34f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun StatusBanner(state: AppUiState, modifier: Modifier = Modifier) {
    val warning = state.cameraStatus == CameraStatus.UNSTABLE || state.cameraStatus == CameraStatus.DEGRADED
    Text(
        text = state.statusMessage.orEmpty(),
        color = if (warning) WarningAmber else Color.White.copy(alpha = 0.82f),
        fontSize = 12.sp,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.66f), PillShape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), PillShape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun ErrorOverlay(message: String?, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f))
            .padding(30.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CAMERA UNAVAILABLE", style = HeadingStyle, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(message ?: "The camera could not be started.", color = MotionMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("CHANGE SETUP", onBack)
    }
}

@Composable
private fun ControlsSheet(state: AppUiState, viewModel: MotionViewModel, onDismiss: () -> Unit) {
    DarkSheet(onDismiss = onDismiss) {
        SheetHeader("CONTROLS", "Tap the live view to open this panel. Long-press it for diagnostics.")
        LabeledSlider(
            label = "Sensitivity",
            valueText = "${(state.settings.sensitivity * 100).roundToInt()}%",
            value = state.settings.sensitivity,
            range = 0.15f..0.95f,
            onValueChange = viewModel::setSensitivity,
        )
        LabeledSlider(
            label = "Minimum object size",
            valueText = "${state.settings.minimumAreaPx} px",
            value = state.settings.minimumAreaPx.toFloat(),
            range = 20f..400f,
            onValueChange = { viewModel.setMinimumArea(it.roundToInt()) },
        )
        LabeledSlider(
            label = "Zoom",
            valueText = String.format(Locale.US, "%.1f×", state.zoomRatio),
            value = state.zoomRatio,
            range = state.minZoomRatio..state.maxZoomRatio.coerceAtLeast(state.minZoomRatio + 0.01f),
            onValueChange = viewModel::setZoom,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetAction(
                text = if (state.torchEnabled) "TORCH ON" else "TORCH OFF",
                enabled = state.torchAvailable,
                onClick = { viewModel.setTorch(!state.torchEnabled) },
                modifier = Modifier.weight(1f),
            )
            SheetAction("RESET BACKGROUND", onClick = viewModel::resetDetection, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel("ADVANCED")
        LabeledSlider(
            label = "Persistence",
            valueText = "${state.settings.persistenceFrames} frames",
            value = state.settings.persistenceFrames.toFloat(),
            range = 1f..8f,
            onValueChange = { viewModel.setPersistence(it.roundToInt()) },
        )
        LabeledSlider(
            label = "Background learning",
            valueText = String.format(Locale.US, "%.3f", state.settings.learningRate),
            value = state.settings.learningRate.toFloat(),
            range = 0.001f..0.02f,
            onValueChange = { viewModel.setLearningRate(it.toDouble()) },
        )
        if (state.mode == MotionMode.HANDHELD) {
            LabeledSlider(
                label = "Stability threshold",
                valueText = "${(state.settings.instabilityThreshold * 100).roundToInt()}%",
                value = state.settings.instabilityThreshold,
                range = 0.25f..0.9f,
                onValueChange = viewModel::setInstabilityThreshold,
            )
        }
        TextButton(onClick = viewModel::restoreDefaults, modifier = Modifier.fillMaxWidth()) {
            Text("RESTORE PROFILE DEFAULTS", color = MotionMuted, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
private fun DiagnosticsSheet(state: AppUiState, onDismiss: () -> Unit) {
    DarkSheet(onDismiss = onDismiss) {
        SheetHeader("DIAGNOSTICS", "Live processing health. A dash means the metric does not apply yet.")
        MetricGrid(
            listOf(
                "ANALYSIS FPS" to if (state.metrics.fps > 0) String.format(Locale.US, "%.1f", state.metrics.fps) else "—",
                "PROCESSING" to if (state.metrics.processingMs > 0) String.format(Locale.US, "%.1f ms", state.metrics.processingMs) else "—",
                "RESOLUTION" to if (state.metrics.width > 0) "${state.metrics.width} × ${state.metrics.height}" else "—",
                "MOTION" to String.format(Locale.US, "%.2f%%", state.metrics.motionPercentage),
                "MEDIAN" to if (state.metrics.medianMs > 0) String.format(Locale.US, "%.1f ms", state.metrics.medianMs) else "—",
                "P95" to if (state.metrics.p95Ms > 0) String.format(Locale.US, "%.1f ms", state.metrics.p95Ms) else "—",
                "STABILITY" to state.metrics.stabilizationConfidence?.let { "${(it * 100).roundToInt()}%" }.orEmpty().ifEmpty { "—" },
                "REJECTED" to state.metrics.rejectedFrames.toString(),
            ),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "${state.mode.name} · ${state.preset.name}${if (state.metrics.resolutionFallback) " · OVER BUDGET" else ""}",
            color = if (state.metrics.resolutionFallback) WarningAmber else MotionMuted,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun ProfileSheet(state: AppUiState, viewModel: MotionViewModel, onDismiss: () -> Unit) {
    DarkSheet(onDismiss = onDismiss) {
        SheetHeader("DETECTION PROFILE", "Changing a profile clears the learned background and prepares again.")
        SectionLabel("CAMERA")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactChoice("Tripod", "Recommended", state.mode == MotionMode.FIXED, { viewModel.setMode(MotionMode.FIXED) }, Modifier.weight(1f))
            CompactChoice("Handheld", "Experimental", state.mode == MotionMode.HANDHELD, { viewModel.setMode(MotionMode.HANDHELD) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel("TARGET MOVEMENT")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactChoice("Crawling", "Persistent", state.preset == TargetPreset.CRAWLING, { viewModel.setPreset(TargetPreset.CRAWLING) }, Modifier.weight(1f))
            CompactChoice("Flying", "Brief", state.preset == TargetPreset.FLYING, { viewModel.setPreset(TargetPreset.FLYING) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DarkSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MotionSurface,
        contentColor = MotionWhite,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.24f), PillShape),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SheetHeader(title: String, detail: String) {
    Text(title, style = HeadingStyle.copy(fontSize = 26.sp))
    Text(detail, color = MotionMuted, fontSize = 13.sp, lineHeight = 19.sp)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MotionWhite, fontSize = 15.sp)
            Text(valueText, color = MotionMuted, fontSize = 13.sp)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MotionWhite,
                activeTrackColor = Color.White.copy(alpha = 0.86f),
                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}

@Composable
private fun SheetAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = PillShape,
        border = ButtonDefaults.outlinedButtonBorder(enabled),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(text, fontSize = 11.sp, letterSpacing = 0.5.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MetricGrid(metrics: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(MotionSurfaceRaised, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                    ) {
                        Text(label, color = MotionGray, fontSize = 10.sp, letterSpacing = 0.9.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(value, color = MotionWhite, fontSize = 20.sp, maxLines = 1)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InformationScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    BrandedScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
        ) {
            Wordmark()
            Spacer(Modifier.height(30.dp))
            Text("HOW MOTION SIGHT WORKS", style = HeadingStyle)
            Spacer(Modifier.height(24.dp))
            InfoSection("WHAT YOU SEE", "White pixels are areas that changed after camera movement and lighting changes have been filtered. Gray pixels are the dim live scene.")
            InfoSection("WHAT IT CANNOT DO", "The prototype does not identify insects. Dust, shadows, leaves, people, reflections, and display flicker can also appear as motion. A motionless insect will not be highlighted.")
            InfoSection("BEST RESULTS", "Keep the phone fixed, move close, use appropriate zoom, add strong steady light, and choose a plain background whenever possible.")
            InfoSection("HANDHELD MODE", "Handheld stabilization is experimental. Move slowly and smoothly. When alignment is unreliable, the app rejects the frame instead of whitening most of the image.")
            InfoSection("PRIVATE BY DESIGN", "Every frame is processed on this device. Motion Sight does not save or upload images or video.")
            Spacer(Modifier.height(20.dp))
            PrimaryButton("BACK", onBack)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    Column(Modifier.padding(bottom = 24.dp)) {
        SectionLabel(title)
        Spacer(Modifier.height(8.dp))
        Text(body, color = MotionMuted, fontSize = 15.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(22.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    }
}

@Composable
private fun BrandedScreen(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MotionBlack)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) { content() }
}

@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "MOTION SIGHT",
            color = MotionGray,
            fontFamily = Poppins,
            fontSize = 19.sp,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = MotionGray, fontSize = 11.sp, letterSpacing = 1.3.sp)
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(containerColor = MotionWhite, contentColor = MotionBlack),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, fontSize = 13.sp)
    }
}

private val HeadingStyle = TextStyle(
    color = MotionWhite,
    fontFamily = Poppins,
    fontSize = 32.sp,
    lineHeight = 38.sp,
)
