package ir.simurgh.photolib.components.shapes

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import ir.simurgh.photolib.utils.XmlPathDataExtractor

class CustomPathShape(val customPath: Path, val viewportWidth: Float, val viewportHeight: Float) :
    BaseShape() {

    private val scaleMatrix by lazy {
        Matrix()
    }

    private val viewportRect by lazy {
        RectF()
    }

    private val resizedRect by lazy {
        RectF()
    }

    constructor(xmlVector: XmlPathDataExtractor.XmlVector) : this(
        xmlVector.path,
        xmlVector.viewportWidth,
        xmlVector.viewportHeight
    )

    init {
        fPath.set(customPath)
        viewportRect.set(0f, 0f, viewportWidth, viewportHeight)
    }

    override fun resize(width: Float, height: Float) {
        super.resize(width, height)
        resizedRect.set(0f, 0f, width, height)
        scaleMatrix.setRectToRect(viewportRect, resizedRect, Matrix.ScaleToFit.FILL)
        customPath.transform(scaleMatrix, fPath)
    }

    override fun clone(): Shape {
        return CustomPathShape(customPath, viewportWidth, viewportHeight).apply {
            resize(desiredWidth, desiredHeight)
        }
    }
}