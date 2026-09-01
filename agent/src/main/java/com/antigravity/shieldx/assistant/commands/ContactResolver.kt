package com.antigravity.shieldx.assistant.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Turns a spoken name into a real phone number.
 *
 * This did not exist before, which is why calling never worked: the call tool
 * built `tel:mom` straight from the spoken words and handed that to the dialer,
 * then reported success. The dialer cannot do anything with "mom", so nothing
 * happened while the assistant claimed it had placed the call.
 */
object ContactResolver {

    private const val TAG = "TarziContacts"

    data class Match(val name: String, val number: String)

    sealed class Result {
        data class Found(val match: Match) : Result()
        data class Ambiguous(val candidates: List<Match>) : Result()
        object NotFound : Result()
        object PermissionMissing : Result()
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Resolve [spokenName] against the contacts database.
     *
     * A literal phone number short-circuits the lookup entirely, so "call
     * 5551234" still works without the contacts permission.
     */
    fun resolve(context: Context, spokenName: String): Result {
        val trimmed = spokenName.trim()
        if (trimmed.isEmpty()) return Result.NotFound

        // Already a number? Dial it directly.
        val digits = trimmed.filter { it.isDigit() || it == '+' }
        if (digits.length >= 5 && digits.length >= trimmed.length - 3) {
            return Result.Found(Match(trimmed, digits))
        }

        if (!hasPermission(context)) return Result.PermissionMissing

        return try {
            queryContacts(context, trimmed)
        } catch (e: Exception) {
            Log.w(TAG, "[LOOKUP_FAIL] " + e.message)
            Result.NotFound
        }
    }

    private fun queryContacts(context: Context, name: String): Result {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        // LIKE rather than equality: people say "mom", the contact is "Mom Mobile".
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$name%")

        val matches = LinkedHashMap<String, Match>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            args,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameCol < 0 || numberCol < 0) return Result.NotFound

            while (cursor.moveToNext() && matches.size < 8) {
                val displayName = cursor.getString(nameCol) ?: continue
                val number = cursor.getString(numberCol) ?: continue
                // One entry per person, even when they have several numbers.
                matches.putIfAbsent(displayName.lowercase(), Match(displayName, number))
            }
        }

        val candidates = matches.values.toList()

        return when {
            candidates.isEmpty() -> Result.NotFound

            candidates.size == 1 -> Result.Found(candidates.first())

            else -> {
                // An exact name match wins over the other partial hits.
                val exact = candidates.firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (exact != null) Result.Found(exact) else Result.Ambiguous(candidates)
            }
        }
    }
}
