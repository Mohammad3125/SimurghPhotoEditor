package ir.manan.mananpic.utils

import android.content.res.Resources
import org.xmlpull.v1.XmlPullParser

class XmlPathDataExtractor {
    companion object {
        fun getPathDataFromXml(resources: Resources, drawableId: Int): String {
            resources.getXml(drawableId).use { parser ->
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        if (parser.name == "path") {
                            return parser.getAttributeValue(
                                findAttributePosition(
                                    "pathData",
                                    parser
                                )
                            )
                        }
                    }
                    event = parser.next()
                }
            }
            throw IllegalStateException("couldn't find any path data")
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
}
