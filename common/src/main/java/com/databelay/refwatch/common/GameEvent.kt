package com.databelay.refwatch.common
import android.os.Parcelable
import com.google.firebase.firestore.Exclude
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.util.UUID

// --- Game Event Sealed Class and its Subclasses ---
@Serializable
sealed class GameEvent : Parcelable {
    abstract val id: String
    abstract val timestamp: Long // Wall-clock time of event logging
    abstract val gameTimeMillis: Long // Game clock time when event occurred
    abstract val displayString: String // User-friendly string for the log
    abstract val eventType: String

    @Serializable
    @Parcelize
    data class GoalScoredEvent(
        override val id: String = UUID.randomUUID().toString(),
        val team: Team,
        override val timestamp: Long = System.currentTimeMillis(),
        override val gameTimeMillis: Long,
        val homeScoreAtTime: Int,
        val awayScoreAtTime: Int,
        // Provide a default value for the type
        override val eventType: String = "GOAL"
    ) : GameEvent() {
        @get:Exclude // Exclude from Firebase automatic mapping
        override val displayString: String
            get() = "Goal: ${team.name} ($homeScoreAtTime-$awayScoreAtTime) at ${gameTimeMillis.formatTime()}"
    }

    @Serializable
    @Parcelize
    data class CardIssuedEvent(
        override val id: String = UUID.randomUUID().toString(),
        val team: Team,
        val playerNumber: Int,
        val cardType: CardType,
        override val timestamp: Long = System.currentTimeMillis(),
        override val gameTimeMillis: Long,
        override val eventType: String = "CARD"
    ) : GameEvent() {
        @get:Exclude
        override val displayString: String
            get() = "${cardType.name.replaceFirstChar { it.uppercase() }} Card: ${team.name}, Player #$playerNumber at ${gameTimeMillis.formatTime()}"
    }

    @Serializable
    @Parcelize
    data class PhaseChangedEvent(
        override val id: String = UUID.randomUUID().toString(),
        val newPhase: GamePhase,
        override val timestamp: Long = System.currentTimeMillis(),
        override val gameTimeMillis: Long,
        override val eventType: String = "PHASE_CHANGE"
    ) : GameEvent() {
        @get:Exclude
        override val displayString: String
            get() = "${newPhase.readable()} (Clock: ${gameTimeMillis.formatTime()})"
    }

    @Serializable
    @Parcelize
    data class GenericLogEvent(
        override val id: String = UUID.randomUUID().toString(),
        val message: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val gameTimeMillis: Long = 0L,
        override val eventType: String = "GENERIC_LOG"
    ) : GameEvent() {
        @get:Exclude
        override val displayString: String
            get() = message
    }
}