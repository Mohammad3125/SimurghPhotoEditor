package ir.manan.mananpic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.widget.FrameLayout
import ir.manan.mananpic.components.MananTextView
import ir.manan.mananpic.components.imageviews.MananImageView

/**
 * A factory class responsible for creating components like [MananTextView], [MananImageView].
 */
class MananFactory {
    companion object {
        /**
         * Creates a [MananImageView] with required layout params.
         */
        fun createImageView(context: Context, bitmap: Bitmap): MananImageView {
            return MananImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setImageBitmap(bitmap)
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    clipToOutline = false
                }
                this.text = text
                setTextColor(Color.BLACK)
            }
        }
    }
}