package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LogLevel
import com.example.model.TerminalLog
import com.example.ui.theme.CyberBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.OnSurfaceWhite
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDim
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalText
import com.example.ui.theme.TerminalYellow
import com.example.viewmodel.ApkBuilderViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TerminalScreen(
    viewModel: ApkBuilderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logs by viewModel.terminalLogs.collectAsState()
    val isBuilding by viewModel.isBuilding.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()

    var inputCommand by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new logs
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
    ) {
        // Terminal Window Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSurface)
                .border(1.dp, CyberCardBorder)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Window dots
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(TerminalRed))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(TerminalYellow))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(TerminalGreen))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = CyberPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "forge:${currentProject.packageName}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceWhite
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Copy logs button
                IconButton(
                    onClick = {
                        val text = logs.joinToString("\n") { "[${it.tag}] ${it.message}" }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Logs", text))
                        Toast.makeText(context, "Copied logs to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("copy_terminal_logs_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Logs",
                        tint = OnSurfaceDim,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Clear logs button
                IconButton(
                    onClick = { viewModel.clearTerminal() },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("clear_terminal_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Terminal",
                        tint = OnSurfaceDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Terminal Log Output Window
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs, key = { it.id }) { log ->
                TerminalLogLine(log = log)
            }
        }

        // Quick Command Pills
        val quickCommands = listOf("build", "verify", "download", "info", "files", "cache", "clean", "help")
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberSurface)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickCommands) { cmd ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberSurfaceVariant)
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(6.dp))
                        .clickable { viewModel.executeTerminalCommand(cmd) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("quick_cmd_$cmd")
                ) {
                    Text(
                        text = "$ $cmd",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyberPrimary
                    )
                }
            }
        }

        // Command Prompt Input Line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberBg)
                .border(1.dp, CyberCardBorder)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "apk-forge $ ",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TerminalCyan,
                fontSize = 13.sp
            )

            OutlinedTextField(
                value = inputCommand,
                onValueChange = { inputCommand = it },
                placeholder = {
                    Text("type command (e.g. build, verify)...", fontSize = 12.sp, color = TerminalDim)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputCommand.isNotBlank()) {
                            viewModel.executeTerminalCommand(inputCommand)
                            inputCommand = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TerminalText,
                    unfocusedTextColor = TerminalText,
                    cursorColor = CyberPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_command_input")
            )

            IconButton(
                onClick = {
                    if (inputCommand.isNotBlank()) {
                        viewModel.executeTerminalCommand(inputCommand)
                        inputCommand = ""
                    }
                },
                modifier = Modifier.testTag("send_command_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Run Command",
                    tint = CyberPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun TerminalLogLine(log: TerminalLog) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val timeStr = remember(log.timestamp) { timeFormat.format(Date(log.timestamp)) }

    val textColor = when (log.level) {
        LogLevel.SUCCESS -> TerminalGreen
        LogLevel.WARNING -> TerminalYellow
        LogLevel.ERROR -> TerminalRed
        LogLevel.COMMAND -> TerminalCyan
        LogLevel.SYSTEM -> CyberPrimary
        LogLevel.INFO -> TerminalText
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "[$timeStr]",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalDim
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "[${log.tag}]",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when (log.tag) {
                    "AXML" -> CyberPrimary
                    "DEX" -> TerminalCyan
                    "SIGN" -> TerminalGreen
                    "PKG", "NET" -> TerminalYellow
                    "VERIFY" -> OnSurfaceWhite
                    "ERROR" -> TerminalRed
                    else -> TerminalDim
                }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = log.message,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
        }

        // If progress is provided (e.g. during package downloading), render mini progress bar
        log.progress?.let { prog ->
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 70.dp, end = 12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { prog },
                    color = CyberPrimary,
                    trackColor = CyberSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(prog * 100).toInt()}%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = CyberPrimary
                )
            }
        }
    }
}
