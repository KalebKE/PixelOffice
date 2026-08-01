package com.pixeloffice.entities

import com.pixeloffice.core.PetConfig
import com.pixeloffice.rendering.PetRenderInfo
import com.pixeloffice.rendering.PetType
import com.pixeloffice.world.Pathfinder

data class PetAnchor(
    val id: String,
    val x: Float,
    val y: Float,
    val restingFacing: String? = null
)

/** Ambient cat or dog that rests at safe anchors and walks the office graph. */
class OfficePet(
    val type: PetType,
    startAnchor: PetAnchor,
    private val pathfinder: Pathfinder,
    private val config: PetConfig,
    private val randomFloat: () -> Float
) : BaseEntity(
    x = startAnchor.x - halfWidthFor(type),
    y = startAnchor.y,
    entityId = "pet_${type.name.lowercase()}"
) {
    companion object {
        private const val BUBBLE_CHECK_SECONDS = 60f
        private const val MAX_BUBBLE_SECONDS = 10f

        private fun halfWidthFor(type: PetType): Float = when (type) {
            PetType.CAT -> 8f
            PetType.DOG -> 12f
        }
    }

    override val spriteHalfWidth: Float = halfWidthFor(type)

    private var waypoints = emptyList<Pair<Float, Float>>()
    private var waypointIndex = 0
    private var moving = false
    private var roamRemaining = nextRoamDelay()
    private var bubbleCheckRemaining = BUBBLE_CHECK_SECONDS
    private var bubbleRemaining = 0f
    private var horizontalFacing = startAnchor.restingFacing
        ?: if (type == PetType.CAT) "right" else "left"
    private var targetRestingFacing: String? = null

    var currentAnchorId: String = startAnchor.id
        private set
    var targetAnchorId: String? = null
        private set

    override fun update(dt: Float) {
        if (moving) {
            updateMovement(dt)
        } else {
            roamRemaining -= dt
        }
        updateBubble(dt)
    }

    private fun updateMovement(dt: Float) {
        val waypoint = waypoints.getOrNull(waypointIndex)
        if (waypoint == null) {
            finishRoam()
            return
        }

        val previousX = x
        if (moveTowards(waypoint.first, waypoint.second, config.walkSpeed.coerceAtLeast(1f), dt)) {
            waypointIndex++
            if (waypointIndex >= waypoints.size) finishRoam()
        }
        when {
            x < previousX -> horizontalFacing = "left"
            x > previousX -> horizontalFacing = "right"
        }
    }

    private fun updateBubble(dt: Float) {
        if (bubbleRemaining > 0f) {
            bubbleRemaining = (bubbleRemaining - dt).coerceAtLeast(0f)
        }
        bubbleCheckRemaining -= dt
        if (bubbleCheckRemaining <= 0f) {
            bubbleCheckRemaining = BUBBLE_CHECK_SECONDS
            if (unitRandom() < 0.5f) {
                bubbleRemaining = unitRandom() * MAX_BUBBLE_SECONDS
            }
        }
    }

    fun isReadyToRoam(): Boolean = !moving && roamRemaining <= 0f

    fun isMoving(): Boolean = moving

    fun chooseIndex(size: Int): Int {
        require(size > 0)
        return (unitRandom() * size).toInt().coerceIn(0, size - 1)
    }

    fun startRoam(target: PetAnchor) {
        targetAnchorId = target.id
        targetRestingFacing = target.restingFacing
        waypoints = pathfinder.calculatePath(
            x + spriteHalfWidth,
            y,
            target.x,
            target.y
        )
        waypointIndex = 0
        moving = waypoints.isNotEmpty()
        if (!moving) {
            x = target.x - spriteHalfWidth
            y = target.y
            finishRoam()
        }
    }

    private fun finishRoam() {
        targetAnchorId?.let { currentAnchorId = it }
        targetRestingFacing?.let { horizontalFacing = it }
        targetAnchorId = null
        targetRestingFacing = null
        waypoints = emptyList()
        waypointIndex = 0
        moving = false
        velocity.x = 0f
        velocity.y = 0f
        roamRemaining = nextRoamDelay()
    }

    private fun nextRoamDelay(): Float {
        val min = minOf(config.roamMinSeconds, config.roamMaxSeconds).coerceAtLeast(0f)
        val max = maxOf(config.roamMinSeconds, config.roamMaxSeconds).coerceAtLeast(min)
        return min + (max - min) * unitRandom()
    }

    private fun unitRandom(): Float = randomFloat().coerceIn(0f, 0.999999f)

    fun getTypedRenderInfo(): PetRenderInfo = PetRenderInfo(
        entityId = entityId,
        type = type,
        x = x,
        y = y,
        facing = horizontalFacing,
        moving = moving,
        restingAnchorId = if (moving) null else currentAnchorId,
        visible = visible,
        showBubble = bubbleRemaining > 0f
    )

    override fun getRenderInfo(): Map<String, Any> = mapOf(
        "type" to type.name.lowercase(),
        "x" to x,
        "y" to y,
        "facing" to horizontalFacing,
        "moving" to moving,
        "visible" to visible
    )
}
