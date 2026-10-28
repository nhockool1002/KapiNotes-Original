package com.kapi.notes.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.kapi.notes.domain.model.Stroke
import com.kapi.notes.domain.model.StrokePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** Builds a smooth Compose [Path] from a stroke's raw sampled points. */
fun strokeToPath(points: List<StrokePoint>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) {
        // A tap with no movement still needs to render as a dot - draw a tiny loop.
        path.lineTo(points.first().x + 0.1f, points.first().y + 0.1f)
        return path
    }
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val midX = (prev.x + curr.x) / 2f
        val midY = (prev.y + curr.y) / 2f
        path.quadraticBezierTo(prev.x, prev.y, midX, midY)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

/** True when any segment of [stroke] passes within [radiusPx] of [point] - used by the eraser. */
fun strokeIntersects(stroke: Stroke, point: Offset, radiusPx: Float): Boolean {
    val pts = stroke.points
    if (pts.isEmpty()) return false
    if (pts.size == 1) {
        return distance(pts[0].x, pts[0].y, point.x, point.y) <= radiusPx
    }
    for (i in 1 until pts.size) {
        if (distanceToSegment(point.x, point.y, pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y) <= radiusPx) {
            return true
        }
    }
    return false
}

private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    return sqrt(dx * dx + dy * dy)
}

private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val abx = bx - ax
    val aby = by - ay
    val apx = px - ax
    val apy = py - ay
    val lenSq = abx * abx + aby * aby
    val t = if (lenSq > 0f) ((apx * abx + apy * aby) / lenSq).coerceIn(0f, 1f) else 0f
    val closestX = ax + abx * t
    val closestY = ay + aby * t
    return distance(px, py, closestX, closestY)
}

/**
 * "Xóa phẳng" (flat erase): instead of deleting [stroke] entirely, cuts out only the portion
 * passing within [radiusPx] of [point] and returns the surviving piece(s) as brand-new strokes
 * (each needs its own id since a single stroke can split into several). Returns null when the
 * eraser didn't touch this stroke at all, so the caller can leave it untouched.
 */
fun splitStrokeByErase(stroke: Stroke, point: Offset, radiusPx: Float): List<Stroke>? {
    val pts = stroke.points
    if (pts.isEmpty()) return null
    if (pts.size == 1) {
        return if (distance(pts[0].x, pts[0].y, point.x, point.y) <= radiusPx) emptyList() else null
    }
    val keep = BooleanArray(pts.size) { true }
    var touched = false
    for (i in 1 until pts.size) {
        if (distanceToSegment(point.x, point.y, pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y) <= radiusPx) {
            keep[i - 1] = false
            keep[i] = false
            touched = true
        }
    }
    if (!touched) return null
    val runs = mutableListOf<List<StrokePoint>>()
    var current = mutableListOf<StrokePoint>()
    for (i in pts.indices) {
        if (keep[i]) {
            current.add(pts[i])
        } else if (current.isNotEmpty()) {
            if (current.size >= 2) runs += current
            current = mutableListOf()
        }
    }
    if (current.size >= 2) runs += current
    return runs.map { runPoints -> stroke.copy(id = java.util.UUID.randomUUID().toString(), points = runPoints) }
}

/** Rotates [point] by [degrees] around [center] - shared by the Lasso selection's free-rotate
 * handle (both the live on-canvas preview and the viewmodel's committed point math). */
fun rotatePointAround(point: Offset, center: Offset, degrees: Float): Offset {
    if (degrees == 0f) return point
    val rad = Math.toRadians(degrees.toDouble())
    val dx = point.x - center.x
    val dy = point.y - center.y
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    return Offset(center.x + dx * cos - dy * sin, center.y + dx * sin + dy * cos)
}

/** Standard ray-casting point-in-polygon test, used by the Lasso tool to pick strokes. */
fun pointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        if ((pi.y > point.y) != (pj.y > point.y) &&
            point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/** A stroke counts as "inside" a lasso selection when at least half its sampled points are. */
fun strokesInsideLasso(strokes: List<Stroke>, lasso: List<Offset>): Set<String> {
    if (lasso.size < 3) return emptySet()
    return strokes.filter { stroke ->
        val pts = stroke.points
        if (pts.isEmpty()) return@filter false
        val insideCount = pts.count { pointInPolygon(Offset(it.x, it.y), lasso) }
        insideCount.toFloat() / pts.size >= 0.5f
    }.map { it.id }.toSet()
}

/** Snaps [end] so the line from [start] to [end] falls on the nearest 45° angle once it's
 * within [toleranceDegrees] of one - lets a slightly wobbly drag still produce a clean
 * horizontal/vertical/diagonal line. */
fun snapLineEnd(start: Offset, end: Offset, toleranceDegrees: Float = 6f): Offset {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return end
    val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    val nearestStep = round(angleDeg / 45f) * 45f
    if (kotlin.math.abs(angleDeg - nearestStep) > toleranceDegrees) return end
    val rad = Math.toRadians(nearestStep.toDouble())
    return Offset(start.x + (cos(rad) * length).toFloat(), start.y + (sin(rad) * length).toFloat())
}

/** The "Compa" (compass) tool: a true center+radius circle, built as a ring of freehand
 * [StrokePoint]s (not a bounding-box CIRCLE shape) so it renders with the currently selected ink
 * tool's own style - pencil translucency, highlighter blend, brush taper - instead of a flat
 * generic outline. [pressureStart]/[pressureEnd] are blended across the ring since a compass
 * stroke has no natural per-point pressure of its own. */
fun circlePoints(center: Offset, edge: Offset, pressureStart: Float, pressureEnd: Float, segments: Int = 72): List<StrokePoint> {
    val radius = (edge - center).getDistance().coerceAtLeast(1f)
    val pressure = ((pressureStart + pressureEnd) / 2f).coerceIn(0.3f, 1f)
    return (0..segments).map { i ->
        val angle = 2.0 * Math.PI * i / segments
        StrokePoint(
            x = center.x + (radius * cos(angle)).toFloat(),
            y = center.y + (radius * sin(angle)).toFloat(),
            pressure = pressure
        )
    }
}

/** Snaps the drag's second corner so a near-square/near-circle bounding box becomes exact. */
fun snapBoundsCorner(start: Offset, end: Offset, toleranceRatio: Float = 0.08f): Offset {
    val width = end.x - start.x
    val height = end.y - start.y
    val avg = (kotlin.math.abs(width) + kotlin.math.abs(height)) / 2f
    if (avg < 1f) return end
    val diff = kotlin.math.abs(kotlin.math.abs(width) - kotlin.math.abs(height))
    if (diff / avg > toleranceRatio) return end
    val side = kotlin.math.max(kotlin.math.abs(width), kotlin.math.abs(height))
    return Offset(
        start.x + (if (width < 0) -side else side),
        start.y + (if (height < 0) -side else side)
    )
}

/** Bounding box of a set of strokes, or null when the list is empty - used to draw the
 * selection outline and to decide whether a new drag should move the selection or start a
 * fresh lasso. */
fun strokesBounds(strokes: List<Stroke>): Rect? {
    val allPoints = strokes.flatMap { it.points }
    if (allPoints.isEmpty()) return null
    val minX = allPoints.minOf { it.x }
    val maxX = allPoints.maxOf { it.x }
    val minY = allPoints.minOf { it.y }
    val maxY = allPoints.maxOf { it.y }
    return Rect(minX, minY, maxX, maxY)
}
