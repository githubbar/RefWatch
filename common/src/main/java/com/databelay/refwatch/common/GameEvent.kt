package com.databelay.refwatch.common

import android.os.Parcelable
import com.google.firebase.firestore.Exclude
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.util.UUID
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.modules.SerializersModule

val gameEventModule = SerializersModule {
    polymorphic(GameEvent::class) {
        subclass(GoalScoredEvent::class)
        subclass(CardIssuedEvent::class)
        subclass(PhaseChangedEvent::class)
        subclass(GenericLogEvent::class)
    }
}
// --- Game Event data Class and its Subclasses ---
@Serializable
sealed class GameEvent : Parcelable {
    abstract val id: String
    abstract val timestamp: Double // Wall-clock time of event logging
    abstract val gameTimeMillis: Double // Game clock time when event occurred
    abstract val displayString: String // User-friendly string for the log
    // No 'eventType' or 'type' abstract val here
}

@Serializable
@SerialName("GOAL") // Value for the class discriminator "eventType"
@Parcelize
data class GoalScoredEvent( // ENSURE NO 'protected', 'private', or 'internal' MODIFIER HERE
    override val id: String = UUID.randomUUID().toString(),
    val team: Team,
    override val timestamp: Double = System.currentTimeMillis().toDouble(),
    override val gameTimeMillis: Double,
    val homeScoreAtTime: Int,
    val awayScoreAtTime: Int
) : GameEvent() {
    @get:Exclude // Exclude from Firebase automatic mapping
    override val displayString: String
        get() = "Goal: ${team.name} ($homeScoreAtTime-$awayScoreAtTime) at ${gameTimeMillis.toLong().formatTime()}"
}

@Serializable
@SerialName("CARD") // Value for the class discriminator "eventType"
@Parcelize
data class CardIssuedEvent( // ENSURE NO 'protected', 'private', or 'internal' MODIFIER HERE
    override val id: String = UUID.randomUUID().toString(),
    val team: Team,
    val playerNumber: Int,
    val cardType: CardType,
    override val timestamp: Double = System.currentTimeMillis().toDouble(),
    override val gameTimeMillis: Double
) : GameEvent() {
    @get:Exclude
    override val displayString: String
        get() = "${cardType.name.replaceFirstChar { it.uppercase() }} Card: ${team.name}, Player #$playerNumber at ${gameTimeMillis.toLong().formatTime()}"
}

@Serializable
@SerialName("PHASE_CHANGE") // Value for the class discriminator "eventType"
@Parcelize
data class PhaseChangedEvent( // ENSURE NO 'protected', 'private', or 'internal' MODIFIER HERE
    override val id: String = UUID.randomUUID().toString(),
    val newPhase: GamePhase,
    override val timestamp: Double = System.currentTimeMillis().toDouble(),
    override val gameTimeMillis: Double
) : GameEvent() {
    @get:Exclude
    override val displayString: String
        get() = "${newPhase.readable()} (Clock: ${gameTimeMillis.toLong().formatTime()})"
}

@Serializable
@SerialName("GENERIC_LOG") // Value for the class discriminator "eventType"
@Parcelize
data class GenericLogEvent( // ENSURE NO 'protected', 'private', or 'internal' MODIFIER HERE
    override val id: String = UUID.randomUUID().toString(),
    val message: String,
    override val timestamp: Double = System.currentTimeMillis().toDouble(),
    override val gameTimeMillis: Double = 0.0
) : GameEvent() {
    @get:Exclude
    override val displayString: String
        get() = message
}

