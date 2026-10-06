// --- FILE: app/src/main/java/com/example/seebeepee/model/Route.kt ---

package com.example.seebeepee.model

data class Route(
    val name: String,
    val waypoints: List<Waypoint>
)