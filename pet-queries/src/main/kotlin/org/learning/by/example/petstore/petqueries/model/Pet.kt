package org.learning.by.example.petstore.petqueries.model

data class Pet(
    val name: String,
    val category: String,
    val breed: String,
    val dob: String,
    val vaccines: List<String>,
    val tags: List<String>? = null
)
