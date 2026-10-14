package com.example.notificationannouncer

internal enum class Honorific(val spoken: String) {
    MR("Mister"),
    MRS("Missus"),
    MS("Miz")
}

internal data class GreetingAddress(val title: Honorific, val name: String)

internal object GreetingText {
    fun cleanedName(input: String): String = input.trim().replace(Regex("\\s+"), " ")

    fun announcement(address: GreetingAddress?, sender: String): String {
        val opening = if (address == null || address.name.isBlank()) {
            "You have"
        } else {
            address.title.spoken + " " + address.name + ", you have"
        }
        return opening + " a message from " + sender + ". Would you like me to read it?"
    }
}
