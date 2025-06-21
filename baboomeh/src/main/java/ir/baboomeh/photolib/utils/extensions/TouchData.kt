package ir.baboomeh.photolib.utils.extensions

import ir.baboomeh.photolib.utils.gesture.TouchData

fun TouchData.isNearPoint(targetX:Float, targetY: Float, range: Float) : Boolean{
    return (ex in (targetX - range)..(targetX + range) && ey in (targetY - range)..(targetY + range))
}