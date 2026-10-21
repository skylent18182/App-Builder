package com.example.model

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    COMMAND,
    SYSTEM
}

data class TerminalLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "BUILD",
    val message: String,
    val progress: Float? = null
)
