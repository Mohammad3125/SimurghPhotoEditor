package ir.manan.mananpic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.shapes.Shape
import android.os.Build
import android.widget.FrameLayout
import androidx.core.view.setPadding
import ir.manan.mananpic.components.MananTextView
import ir.manan.mananpic.components.imageviews.MananCustomImageView
import ir.manan.mananpic.components.imageviews.MananShapeView
import kotlin.math.max

/**
 * A factory class responsible for creating components like [MananTextView], [MananCustomImageView].
 */
class MananFactory {
    companion object {
        /**
         * Creates a [MananCustomImageView] with required layout params.
         */
        fun createImageView(context: Context, bitmap: Bitmap): MananCustomImageView {
            return MananCustomImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                this.bitmap = bitmap
            }
        }

        /**
         * Creates a [MananTextView] with required layout params.
         */
        fun createTextView(context: Context, text: String): MananTextView {
            return MananTextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                val ds = context.resources.displayMetrics
                setPadding(max(ds.widthPixels, ds.heightPixels))
                this.text = text
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    clipToOutline = false
                }
            }
        }

        fun createShapeView(
            context: Context,
            shape: Shape,
            shapeWidth: Int,
            shapeHeight: Int
        ): MananShapeView {
            return MananShapeView(context, shape, shapeWidth, shapeHeight).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }
        }
    }
}