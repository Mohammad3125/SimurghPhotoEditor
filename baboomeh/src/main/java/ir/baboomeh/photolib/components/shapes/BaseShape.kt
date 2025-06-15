package ir.baboomeh.photolib.components.shapes

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path

open class BaseShape : Shape() {

    protected val fPath = Path()

    protected val copyPath = Path()

    protected var desiredWidth = 0f
    protected var desiredHeight = 0f

    protected var isPathResized = false

    override fun resize(width: Float, height: Float) {
        desiredWidth = width
        desiredHeight = height

        isPathResized = true
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawPath(fPath, paint)
    }

    override fun getPath(): Path {
        if (!isPathResized) throw IllegalStateException("Shape should be resized before calling converting to path")

        return fPath
    }

    override fun drawToPath(path: Path, transform: Matrix?) {
        copyPath.set(fPath)
        if (transform != null) {
            copyPath.transform(transform)
        }
        path.addPath(copyPath, 0f, 0f)
    }

    override fun clone(): Shape {
        return super.clone() as Shape
    }
}