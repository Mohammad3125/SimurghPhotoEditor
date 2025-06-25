package ir.baboomeh.photolib.components.paint.painters.painter

interface MessageChannel {
    /**
     * Called when a painter sends a message.
     *
     * @param message The message sent by the painter
     */
    fun onSendMessage(message: PainterMessage)
}