package ir.manan.mananpic.utils

import android.content.res.Resources
import android.graphics.Path
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser

/**
 * Class that is responsible for extracting path data from a vector xml.
 */
class XmlPathDataExtractor {
    companion object {
        /**
         * Retrieves path data from an xml resources.
         * @param resources Resources object.
         * @param drawableId Id of vector drawable that contains path data.
         * @return Extracted path data from given drawable id.
         */
        fun getVectorFromXml(resources: Resources, drawableId: Int): XmlVector {
            var width = 0f
            var height = 0f
            var pathData = ""
            return resources.getXml(drawableId).use { parser ->
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        if (parser.name == "path") {
                            pathData = parser.getAttributeValue(
                                findAttributePosition(
                                    "pathData",
                                    parser
                                )
                            )
                        }
                        if (parser.name == "vector") {
                            width = parser.getAttributeValue(
                                findAttributePosition("viewportWidth", parser)
                            ).toFloat()

                            height = parser.getAttributeValue(
                                findAttributePosition("viewportHeight", parser)
                            ).toFloat()
                        }
                    }
                    event = parser.next()
                }
                XmlVector(PathParser.createPathFromPathData(pathData), width, height)
            }
        }

        /**
         * Finds index of given attribute name inside xml.
         * @param attributeName Name of attribute to find index of it.
         * @param pullParser Parser that contains xml.
         * @return Index of given attribute name. Returns -1 if it didn't find any attribute with given name.
         */
        private fun findAttributePosition(attributeName: String, pullParser: XmlPullParser): Int {
            return (0 until pullParser.attributeCount).firstOrNull { i ->
                pullParser.getAttributeName(
                    i
                ) == attributeName
            }
                ?: -1
        }
    }

    data class XmlVector(val path: Path, val viewportWidth: Float, val viewportHeight: Float)
}
