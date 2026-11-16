// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.impl.storage

import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.foundation.common.input.BindableKey
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val MAX_PROFILE_PAYLOAD_BYTES: Int = 65_536

internal sealed interface ProfileEncodeResult {
    data class Encoded(val payload: String) : ProfileEncodeResult
    data class Rejected(val reason: ProfileSnapshotRejection) : ProfileEncodeResult
}

internal sealed interface ProfileDecodeResult {
    data class Decoded(val snapshot: ProfileSnapshot) : ProfileDecodeResult
    data class Rejected(val reason: ProfileSnapshotRejection) : ProfileDecodeResult
}

/** Strict, canonical codec for the demo profile. */
internal object ProfileCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        allowSpecialFloatingPointValues = false
        prettyPrint = false
    }

    fun encode(snapshot: ProfileSnapshot, policy: ProfilePolicySnapshot): ProfileEncodeResult {
        val dto = try {
            snapshot.toDto(policy)
        } catch (rejection: ProfileCodecRejection) {
            return ProfileEncodeResult.Rejected(rejection.reason)
        }
        val payload = try {
            json.encodeToString(dto)
        } catch (_: SerializationException) {
            return ProfileEncodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileEncodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        }
        return when (payload.utf8Validation()) {
            Utf8Validation.Accepted -> ProfileEncodeResult.Encoded(payload)
            Utf8Validation.TooLarge -> ProfileEncodeResult.Rejected(ProfileSnapshotRejection.PAYLOAD_TOO_LARGE)
            Utf8Validation.Invalid -> ProfileEncodeResult.Rejected(ProfileSnapshotRejection.INVALID_UTF8)
        }
    }

    fun decode(payload: String, policy: ProfilePolicySnapshot): ProfileDecodeResult {
        when (payload.utf8Validation()) {
            Utf8Validation.TooLarge -> {
                return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.PAYLOAD_TOO_LARGE)
            }
            Utf8Validation.Invalid -> {
                return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.INVALID_UTF8)
            }
            Utf8Validation.Accepted -> Unit
        }

        val dto = try {
            json.decodeFromString<ProfileSnapshotDto>(payload)
        } catch (_: SerializationException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        }

        val canonicalPayload = try {
            json.encodeToString(dto)
        } catch (_: SerializationException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        } catch (_: IllegalArgumentException) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.MALFORMED_JSON)
        }
        if (canonicalPayload != payload) {
            return ProfileDecodeResult.Rejected(ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD)
        }

        return try {
            ProfileDecodeResult.Decoded(dto.toSnapshot(policy))
        } catch (rejection: ProfileCodecRejection) {
            ProfileDecodeResult.Rejected(rejection.reason)
        }
    }
}

/**
 * An optional preference: absence is [default]; a stored value equal to it is never written, so it
 * is refused rather than silently accepted.
 */
internal fun <T : Any> T?.storedOr(default: T): T = when (this) {
    null -> default
    default -> reject(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    else -> this
}

/**
 * Strict mapping of the rebound actions over [KeyBindings.DEFAULT]: strictly sorted by action wire id
 * (no duplicates), every id known, no record repeating its default, and the resulting map binds each
 * key at most once.
 */
internal fun List<KeyBindingDto>.toKeyBindings(): KeyBindings {
    rejectUnless(map(KeyBindingDto::action).isStrictlySorted(), ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE)
    rejectUnless(size <= GameAction.entries.size, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    val keys = KeyBindings.DEFAULT.keys.toMutableList()
    forEach { record ->
        val action = record.action.gameAction()
        val key = BindableKey.fromWireId(record.key) ?: reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
        rejectUnless(key != KeyBindings.DEFAULT.key(action), ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
        keys[action.ordinal] = key
    }
    rejectUnless(KeyBindings.isValid(keys), ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE)
    return KeyBindings.of(keys)
}

/** Maps positive ranks by stable id; a rank of zero is represented by an absent record. */
internal fun List<ProfileRankDto>.toRanks(size: Int, ordinal: (String) -> Int): List<Int> {
    rejectUnless(this.size <= size, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    rejectUnless(map(ProfileRankDto::id).isStrictlySorted(), ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE)
    val ranks = MutableList(size) { 0 }
    for (record in this) {
        rejectUnless(record.rank > 0, ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
        ranks[ordinal(record.id)] = record.rank
    }
    return ranks
}

private class ProfileCodecRejection(
    val reason: ProfileSnapshotRejection,
) : RuntimeException()

internal fun reject(reason: ProfileSnapshotRejection): Nothing = throw ProfileCodecRejection(reason)

internal fun rejectUnless(condition: Boolean, reason: ProfileSnapshotRejection) {
    if (!condition) reject(reason)
}

private enum class Utf8Validation { Accepted, TooLarge, Invalid }

private fun String.utf8Validation(): Utf8Validation {
    var byteCount = 0
    var index = 0
    while (index < length) {
        val character = this[index]
        val encodedBytes = when {
            character.code <= 0x7F -> 1
            character.code <= 0x7FF -> 2
            character.isHighSurrogate() -> {
                if (index + 1 >= length || !this[index + 1].isLowSurrogate()) {
                    return Utf8Validation.Invalid
                }
                index += 1
                4
            }
            character.isLowSurrogate() -> return Utf8Validation.Invalid
            else -> 3
        }
        if (byteCount > MAX_PROFILE_PAYLOAD_BYTES - encodedBytes) return Utf8Validation.TooLarge
        byteCount += encodedBytes
        index += 1
    }
    return Utf8Validation.Accepted
}
