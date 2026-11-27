package com.mojatv.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Rozpoznaje telewizor (Fire TV, Android TV, Google TV). */
object Device {
    @Volatile private var cached: Boolean? = null

    fun isTv(context: Context): Boolean {
        cached?.let { return it }
        val pm = context.packageManager
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val tv = pm.hasSystemFeature("amazon.hardware.fire_tv") ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            Build.MODEL.startsWith("AFT") ||
            ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        cached = tv
        return tv
    }

    val name: String
        get() = if (Build.MODEL.startsWith("AFT")) "Fire TV" else Build.MODEL
}

/** true, gdy aplikacja działa na telewizorze i jest obsługiwana pilotem. */
val LocalIsTv = staticCompositionLocalOf { false }

/**
 * Na telewizorze element wybrany pilotem musi być wyraźnie widoczny:
 * białe obramowanie i rozjaśnienie zamiast ledwo widocznego cienia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvFocusProvider(isTv: Boolean, content: @Composable () -> Unit) {
    if (!isTv) {
        CompositionLocalProvider(LocalIsTv provides false, content = content)
        return
    }
    CompositionLocalProvider(
        LocalIsTv provides true,
        LocalIndication provides TvFocusIndication,
        LocalRippleConfiguration provides RippleConfiguration(
            color = Color.White,
            rippleAlpha = RippleAlpha(
                draggedAlpha = 0.16f,
                focusedAlpha = 0.38f,
                hoveredAlpha = 0.08f,
                pressedAlpha = 0.24f,
            ),
        ),
        content = content,
    )
}

private object TvFocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = TvFocusNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 7
}

private class TvFocusNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private var focused = false
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            val focuses = mutableListOf<FocusInteraction.Focus>()
            val presses = mutableListOf<PressInteraction.Press>()
            source.interactions.collect { i ->
                when (i) {
                    is FocusInteraction.Focus -> focuses.add(i)
                    is FocusInteraction.Unfocus -> focuses.remove(i.focus)
                    is PressInteraction.Press -> presses.add(i)
                    is PressInteraction.Release -> presses.remove(i.press)
                    is PressInteraction.Cancel -> presses.remove(i.press)
                }
                val f = focuses.isNotEmpty()
                val p = presses.isNotEmpty()
                if (f != focused || p != pressed) {
                    focused = f
                    pressed = p
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val r = CornerRadius(8.dp.toPx())
        if (focused) {
            drawRoundRect(Color.White.copy(alpha = 0.14f), cornerRadius = r)
            val w = 3.dp.toPx()
            drawRoundRect(
                Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                cornerRadius = r,
                style = Stroke(width = w),
            )
        }
        if (pressed) drawRoundRect(Color.White.copy(alpha = 0.2f), cornerRadius = r)
    }
}
