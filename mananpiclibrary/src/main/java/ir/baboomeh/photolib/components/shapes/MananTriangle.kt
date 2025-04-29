package ir.baboomeh.photolib.components.shapes

class MananTriangle : MananBaseShape() {
    override fun resize(width: Float, height: Float) {
        super.resize(width, height)
        fPath.rewind()

        if (desiredHeight > 0f) {
            fPath.moveTo(0f, desiredHeight)
            fPath.lineTo(desiredWidth * 0.5f, 0f)
            fPath.lineTo(desiredWidth, desiredHeight)
            fPath.lineTo(0f, desiredHeight)
        } else {
            fPath.moveTo(0f, 0f)
            fPath.lineTo(desiredWidth * 0.5f, desiredHeight)
            fPath.lineTo(desiredWidth, 0f)
            fPath.lineTo(0f, 0f)
        }


    }

    override fun clone(): MananTriangle {
        return MananTriangle().apply {
            resize(desiredWidth, desiredHeight)
        }
    }
}