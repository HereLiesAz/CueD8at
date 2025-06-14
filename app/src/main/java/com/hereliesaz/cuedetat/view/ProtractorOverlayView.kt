// hereliesaz/cued8at/CueD8at-66142b655f7e247d83b8004a442ad41e04dd6348/app/src/main/java/com/hereliesaz/cuedetat/view/ProtractorOverlayView.kt
package com.hereliesaz.cuedetat.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.hereliesaz.cuedetat.R
import com.hereliesaz.cuedetat.ui.ZoomMapping
import com.hereliesaz.cuedetat.view.renderer.OverlayRenderer
import com.hereliesaz.cuedetat.view.renderer.util.DrawingUtils
import com.hereliesaz.cuedetat.view.state.OverlayState

@SuppressLint("ClickableViewAccessibility")
class ProtractorOverlayView(context: Context) : View(context) {

    private val renderer = OverlayRenderer()
    private val paints = PaintCache()
    private var state = OverlayState()
    private var barbaroTypeface: Typeface? = null

    // Callbacks to the ViewModel
    var onSizeChanged: ((Int, Int) -> Unit)? = null
    var onRotationChange: ((Float) -> Unit)? = null
    var onUnitMove: ((PointF) -> Unit)? = null
    var onActualCueBallMoved: ((PointF) -> Unit)? = null
    var onZoomChange: ((Float) -> Unit)? = null // Add this callback

    // Gesture detection
    private val scaleGestureDetector: ScaleGestureDetector
    private var lastTouchX = 0f
    private var pointerId = -1

    private enum class DragMode { NONE, ROTATE, MOVE_UNIT, MOVE_ACTUAL_CUE_BALL }

    private var dragMode = DragMode.NONE

    init {
        if (!isInEditMode) {
            barbaroTypeface = ResourcesCompat.getFont(context, R.font.barbaro)
            paints.setTypeface(barbaroTypeface)
        }
        // Initialize the gesture detector
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentZoom = ZoomMapping.sliderToZoom(state.zoomSliderPosition)
            val newZoom = currentZoom * detector.scaleFactor
            val newSliderPos = ZoomMapping.zoomToSlider(newZoom)
            // Invoke the callback with the new slider position
            onZoomChange?.invoke(newSliderPos.coerceIn(0f, 100f))
            return true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.draw(canvas, state, paints, barbaroTypeface)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        onSizeChanged?.invoke(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state.viewWidth == 0 || !state.hasInverseMatrix) return false

        // Pass all touch events to the ScaleGestureDetector.
        scaleGestureDetector.onTouchEvent(event)

        // If a scale gesture is in progress, consume the event and don't process other gestures.
        if (scaleGestureDetector.isInProgress) {
            dragMode = DragMode.NONE
            return true
        }

        // Let other gestures proceed only if scale is not being handled
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                lastTouchX = event.x
                val touchPoint = PointF(event.x, event.y)

                val projectedTargetCenter =
                    DrawingUtils.mapPoint(state.protractorUnit.center, state.pitchMatrix)
                val projectedActualCueCenter = state.actualCueBall?.let {
                    DrawingUtils.mapPoint(it.center, state.pitchMatrix)
                }

                val touchRadius = DrawingUtils.getPerspectiveRadiusAndLift(
                    state.protractorUnit,
                    state,
                ).radius * 2.0f

                dragMode = when {
                    state.actualCueBall != null && projectedActualCueCenter != null && distance(
                        touchPoint,
                        projectedActualCueCenter
                    ) < touchRadius -> DragMode.MOVE_ACTUAL_CUE_BALL

                    distance(touchPoint, projectedTargetCenter) < touchRadius -> DragMode.MOVE_UNIT
                    else -> DragMode.ROTATE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pointerId != -1) {
                    val pointerIndex = event.findPointerIndex(pointerId)
                    if (pointerIndex != -1) {
                        val newX = event.getX(pointerIndex)
                        val newY = event.getY(pointerIndex)

                        when (dragMode) {
                            DragMode.MOVE_UNIT -> onUnitMove?.invoke(PointF(newX, newY))
                            DragMode.MOVE_ACTUAL_CUE_BALL -> {
                                val logicalPos = FloatArray(2)
                                state.inversePitchMatrix.mapPoints(
                                    logicalPos,
                                    floatArrayOf(newX, newY)
                                )
                                onActualCueBallMoved?.invoke(
                                    PointF(
                                        logicalPos[0],
                                        logicalPos[1]
                                    )
                                )
                            }
                            DragMode.ROTATE -> {
                                val dx = newX - lastTouchX
                                lastTouchX = newX
                                val rotationDelta = -dx * 0.2f
                                onRotationChange?.invoke(state.protractorUnit.rotationDegrees + rotationDelta)
                            }

                            else -> {}
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerId = -1
                dragMode = DragMode.NONE
            }
        }
        return true
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        return DrawingUtils.distance(p1, p2)
    }

    fun updateState(newState: OverlayState) {
        this.state = newState
        paints.updateColors(newState.dynamicColorScheme)
        invalidate()
    }
}