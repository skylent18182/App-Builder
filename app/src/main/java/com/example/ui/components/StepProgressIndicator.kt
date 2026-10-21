package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BuildStep
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberError
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.CyberSecondary
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.TerminalDim

@Composable
fun StepProgressIndicator(
    currentStep: BuildStep,
    isBuilding: Boolean,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        BuildStep.INSPECT_ZIP,
        BuildStep.VERIFY_INTEGRITY,
        BuildStep.DOWNLOAD_PACKAGES,
        BuildStep.COMPILE_RESOURCES,
        BuildStep.BUILD_DEX,
        BuildStep.SIGN_APK
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CyberSurfaceVariant)
            .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BUILD PIPELINE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = CyberPrimary,
                letterSpacing = 1.sp
            )
            Text(
                text = when {
                    currentStep == BuildStep.FINISHED -> "COMPLETED (6/6)"
                    currentStep == BuildStep.FAILED -> "FAILED"
                    isBuilding -> "STEP ${currentStep.stepNumber}/6"
                    else -> "STANDBY"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    currentStep == BuildStep.FINISHED -> CyberSecondary
                    currentStep == BuildStep.FAILED -> CyberError
                    isBuilding -> CyberPrimary
                    else -> TerminalDim
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = currentStep.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Multi-segment progress bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            steps.forEach { step ->
                val stepState = when {
                    currentStep == BuildStep.FAILED && step.stepNumber == currentStep.stepNumber -> StepState.FAILED
                    currentStep == BuildStep.FINISHED || currentStep.stepNumber > step.stepNumber -> StepState.COMPLETED
                    isBuilding && currentStep.stepNumber == step.stepNumber -> StepState.ACTIVE
                    else -> StepState.PENDING
                }

                val segColor by animateColorAsState(
                    targetValue = when (stepState) {
                        StepState.COMPLETED -> CyberSecondary
                        StepState.ACTIVE -> CyberPrimary
                        StepState.FAILED -> CyberError
                        StepState.PENDING -> CyberCardBorder
                    },
                    label = "segColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(segColor)
                        .then(
                            if (stepState == StepState.ACTIVE) Modifier.alpha(pulseAlpha) else Modifier
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Steps label summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val stepNames = listOf("ZIP", "Verify", "Packages", "AXML", "DEX", "Sign")
            stepNames.forEachIndexed { index, name ->
                val num = index + 1
                val isDone = currentStep.stepNumber > num || currentStep == BuildStep.FINISHED
                val isCur = currentStep.stepNumber == num && isBuilding
                Text(
                    text = name,
                    fontSize = 11.sp,
                    fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isDone -> CyberSecondary
                        isCur -> CyberPrimary
                        else -> TerminalDim
                    }
                )
            }
        }
    }
}

private enum class StepState {
    PENDING, ACTIVE, COMPLETED, FAILED
}
