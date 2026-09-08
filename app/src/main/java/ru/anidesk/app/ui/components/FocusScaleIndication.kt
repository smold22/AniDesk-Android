package ru.anidesk.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

class FocusScaleIndication(
    private val scale: Float = 1.08f,
    private val animationMillis: Int = 150,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FocusScaleNode(interactionSource, scale, animationMillis)

    override fun equals(other: Any?): Boolean =
        other is FocusScaleIndication &&
            other.scale == scale &&
            other.animationMillis == animationMillis

    override fun hashCode(): Int = scale.hashCode() * 31 + animationMillis
}

private class FocusScaleNode(
    private val interactionSource: InteractionSource,
    private val scale: Float,
    private val animationMillis: Int,
) : Modifier.Node(), DrawModifierNode {

    private val scaleAnim = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val target = if (interaction is FocusInteraction.Focus) 1f else 0f
                launch {
                    scaleAnim.animateTo(target, tween(animationMillis))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val current = 1f + (scale - 1f) * scaleAnim.value
        if (current != 1f) {
            scale(
                scale = current,
                pivot = Offset(size.width / 2f, size.height / 2f),
            ) {
                this@draw.drawContent()
            }
        } else {
            drawContent()
        }
    }
}