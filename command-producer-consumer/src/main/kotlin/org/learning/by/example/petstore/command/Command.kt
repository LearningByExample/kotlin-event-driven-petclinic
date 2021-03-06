package org.learning.by.example.petstore.command

import java.time.Instant
import java.util.UUID

data class Command(val commandName: String, val payload: HashMap<String, Any>) {
    val id: UUID = UUID.randomUUID()
    val timestamp: Instant = Instant.now()

    inline fun <reified T : Any> get(attribute: String): T = if (payload.containsKey(attribute)) {
        with(payload[attribute]!!) {
            return if (this is T) {
                this
            } else {
                throw ClassCastException("attribute '$attribute' is not of ${T::class} is ${this::class}")
            }
        }
    } else {
        throw NoSuchElementException("attribute '$attribute' not found")
    }

    inline fun <reified T : Any> getList(attribute: String) = with(get<List<Any>>(attribute)) {
        if (this.all { it is T }) {
            @Suppress("UNCHECKED_CAST")
            this as List<T>
        } else {
            throw ClassCastException("attribute '$attribute' is not a List of ${T::class}")
        }
    }

    fun contains(attribute: String) = payload.containsKey(attribute)
}
