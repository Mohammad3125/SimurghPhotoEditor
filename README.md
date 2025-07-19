# Simurgh Photo Editing Library

[![](https://jitpack.io/v/Mohammad3125/SimurghPhotoEditor.svg)](https://jitpack.io/#Mohammad3125/SimurghPhotoEditor)
[![License](https://img.shields.io/badge/License-MIT%20-white.svg)](https://opensource.org/licenses/Apache-2.0)
[![API](https://img.shields.io/badge/API-21%2B-red.svg?style=flat)](https://android-arsenal.com/api?level=21)

**Simurgh** is a powerful, feature-rich photo editing library for Android that provides
image manipulation capabilities through set of tools. Built entirely in Kotlin,
it supports features like layers, complex gesutres, drawing, masking, selection, transformations, cropper, etc.

## 📋 Table of Contents

1. **[🚀 Features](#-features)**

2. **[🏗 Architecture](#-architecture)**

3. **[🎨 Core Tools](#-core-tools)**
   - **[👨‍🎨 PainterViews](#-painterviews)**
      - PainterView
      - LayeredPainterView
   - **[🖌️ Brush Painter](#%EF%B8%8F-brush-painter)**
      - Various brush types
      - Brush properties
      - Texture support
   - **[🖌️ BrushPreview](#%EF%B8%8F-brushpreview)**
   - **[📐 Transform Tool](#-transform-tool)**
      - TextTransformable
      - BitmapTransformable
      - ShapeTransformable
      - Default Bursh Stamps
   - **[✂️ Crop Tool](#%EF%B8%8F-crop-tool)**
   - **[🔍 ColorDropper](#-colordropper)**
   - **[🤠 LassoTool](#-lassotool)**
   - **[🤠 LassoColorPainter](#-lassocolorpainter)**
   - **[🧺 FloodFill Painter (Bucket Tool)](#-floodfill-patiner-bucket-tool)**
   - **[🌓 MaskModifierTool](#-maskmodifiertool)**
   - **[🎭 BitmapMaskModifierTool](#-bitmapmaskmodifiertool)**
   - **[🌈 GradientSlider](#-gradientslider)**
   - **[🛠 Custom Painter](#-custom-painter)**

4. **[🛠 Installation](#-installation)**
   - JitPack repository setup
   - Dependency configuration

5. **[🎯 Quick Start](#-quick-start)**
   - Basic setup
   - Export results

6. **[🐛 Troubleshooting](#-troubleshooting)**
   - Common issues

7. **[📄 License](#-license)**

8. **[🤝 Contributing](#-contributing)**

9. **[📞 Support](#-support)**

10. **[🏆 Acknowledgments](#-acknowledgments)**
    - Apps built with Simurgh

## 🚀 Features

- **Layer Management**: Unlimited layers with individual settings.
- **Layer settings**: Opacity, Locking and blending modes supported for each layer.
- **Multi-touch Support**: Zoom, pan, rotation with smooth animations.
- **Extra Gestures for Customizablity**: Double-tap, double-finger-tap, etc.
- **Smart Caching**: Automatic layer and rendering optimization.
- **History Support**: Efficient undo/redo support for most tools.
- **Optimized Drawing**: Optimized drawing engines for minimum latency.
- **Customizable Visuals**: All tools support customizablity for their visual representation.
- **Customizable Behaviour**: All tools support options to change and customize behaviour.
- **Custom Tools**: Support for creating custom tools by user.

## 🏗 Architecture

This library supports various tools that are called `Painters`. These `Painters` take gestures and layers from `PainterView` or `LayeredPainterView`
and modify its content through a `PaintLayer` which contains a `Bitmap` and other information about a layer. `Painters` communicate with `PainterViews`
through a `MessageChannel` for redraw, history save and caching. `PainterViews` save history of each layer but a `Painter` can also trigger history save
in `PainterViews` but they can also manage their own history.

## 🎨 Core Tools

### 👨‍🎨 PainterViews

- **Layer Support**: Manages layers and passes them to `Painters`.
- **Full Gestures**: Support for panning, zooming, rotating and other complex gesture available to user.
- **Animations**: Support for animating the canvas and layout changes.
- **Painter Manager**: Manages the painters and correclty manages their life-cycle.
- **Performance Optimization**: Caching mechanism for layers.

Core component in the Simurgh library that manages the `Painter` objects. `Painter` object take gestures and layers and other options from this component
and it is necesarry to always run `Painter` objects inside any `PainterViews`. This class depends on `PaintLayer` class that represents a layer in the framework.
It manages the opacity, lock-state and blending mode of a `PaintLayer`.

- **`PainterView`**: PainterView with that supports only one layer with full gesture support.
- **`LayeredPainterView`**: A multi-layer PainterView with history management and advanced layer managing capabilities.

**Architecture**
```
View
 └── PainterView
      └── LayeredPainterView
```

<details>
<summary>
  Core PainterView Methods
</summary>

```kotlin
  // Painter property controls which painter is currently displayed and managed.
painterView.painter = BrushPainter()

// Add layer with provided bitmap. A PaintLayer will be create based on this bitmap.
painterView.addNewLayer(bitmap)

// Get the active layer in painterView.
val currentLayer = painterView.selectedLayer

// Add layer with provided PaintLayer.
painterView.addNewLayer(paintLayer)

// Control gesture detection.
painterView.isRotatingEnabled = true
painterView.isScalingEnabled = true
painterView.isTranslationEnabled = true

// Draws a checker board behind every layer, so transparent areas would appear as checker board pattern.
painterView.isCheckerBoardEnabled = true

// Controls if gestures of this PainterView can be delegated to user for handling instead of passing it to `Painter`.
painterView.isGestureDelegationEnabled = true

// After enabling the delegation, any transformation by `PainterView` is available to this callback.
painterView.setOnTransformDelegate { matrix ->
   // Transform anything.
}

// Android system always discards some TouchEvents for the sake of performance, if you need the full TouchEvents to be processed
// You can use this property to control it. It might affect performance because there is more events to map and handle.
painterView.isTouchEventHistoryEnabled = true

// Control animation duration and interpolator.
painterView.matrixAnimationDuration = 500L
painterView.matrixAnimationInterpolator = FastOutSlowInInterpolator()

// Current clip bounds of this painterView.
val rect = painterView.layerClipBounds.toRect() // Always make a copy.
val identityClip = painterView.identityClip

// Reset painter state.
painterView.resetPainter()

// Change properties of layers.
painterView.setSelectedLayerLockState(lockedState = true)
painterView.setSelectedLayerOpacity(opactiy = 0.4f) // Between 0f to 1f.
// Some modes require painterView layer type to run in software mode, change painterView layerType for software for
// blending modes that are not hardware-accelerated on certain API levels.
painterView.setSelectedLayerBlendingMode(blendingMode = PorterDuff.Mode.LIGHTEN)

if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && paintView.layerType == LAYER_TYPE_NONE) {
    paintView.setLayerType(LAYER_TYPE_SOFTWARE, null)
}

// Get layer property
val blendingMode : PorterDuff.Mode = painterView.getSelectedLayerBlendingMode()
val isLayerLocked: Boolean = painterView.getSelectedLayerLockState()
val layerOpacity : Float = painterView.getSelectedLayerOpacity()
val layerBitmap : Bitmap = painterView.getSelectedLayerBitmap(/* Optional argument */ isClipped = true ) // If set to true the bitmap is clipped with current clip available.

// Complex gesture listener.

// Called when user quickly taps on the screen with two fingers. Some drawing apps customize this gesture and undo the history when the gesture occurs.
painterView.setOnDoubleFingerTapUpListener {

}

// When user double taps the painterView. 
painterView.setOnDoubleTapListener {

}

// Convert to bitmap
val layerBitmap = painterView.convertToBitmap()

// Reset transformation matrix to its identity.
painterView.resetTransformationMatrix(animate = true /* If set to true, then painterView animates this change */ )

// Callback invoked when transformationMatrix is reset.
painterView.doAfterResetTransformation {
   // Do anything after reset.
}

// Apply transformation matrix to canvas matrix.
painterView.concatCanvasMatrix(matrix)
painterView.setCanvasMatrix(matrix)

// Set clip with animations
painterView.setClipRect(targetClipRect, animate = true) {
   // This block is invoked after the clip is set (after animation.)
}
```
</details>

<details>
<summary>
  Core LayeredPainterView Methods
</summary>

```kotlin
  // LayeredPainterView inherits all the methods described above from PainterView.
  // These are additional methods from LayeredPainterView

  // Set if caching is enabled. You can benefit from performance gain if you have more than 3 layers in your layeredPainterView.
  // Consider enabling this option when you have enough memory and more than 3 layeres inside your layeredPainterView.
  // Take into consideration that the caching mechanism creates two bitmaps for caching on top of available layers.
  layeredPainterView.isCachingEnabled = true

  // Undo/Redo
  // Performs undo/redo operation on the layers or delegates to painter if it handles history.
  layeredPainterView.undo()
  layeredPainterView.redo()

  // Listener for when undo and redo state holder size changes. You can animate or disable buttons if there isn't anything to undo or redo.
  layeredPainterView.setOnUndoOrRedoListener { isUndoEnabled, isRedoEnabled ->
    
  }

  // Check if any layers inside layeredPainterView is blending. You might use this to change layer-type of layeredPainterView when there is blending on lower APIs of Android.
  val isBlending = painterView.isAnyLayerBlending()

  // Create a new layer based on properties of the first added layer.
  // Note that if you call this method before any 'addNewLayer(layer) or addNewLayer(bitmap)' this method throws an exception.
  layeredPainterView.addNewLayer()

  // Add layer without saving it in the history management system.
  layeredPainterView.addNewLayerWithoutSavingHistory() // Based on previous layers
  layeredPainterView.addNewLayerWithoutSavingHistory(layer: PaintLayer)

  // Get layer properties at specific index inside layers.
  val layerOpacity : Float = layeredPainterView.getLayerOpacityAt(index)
  val layerLockedState : Boolean = layeredPainterView.isLayerLockedAt(index)
  val blendingMode : PorterDuff.Mode = layeredPainterViewgetLayerBlendingModeAt(index)

  // Set layer properties at specific index inside layers.
  layeredPainterView.setLayerOpacityAt(index, opacity)
  layeredPainterView.setLayerBlendingModeAt(index,mode)
  layeredPainterView.setLayerLockedStateAt(index,lockState)

  // Change selected layer bitmap
  layeredPainterView.setSelectedLayerBitmap(bitmap: Bitmap)

  // Convert all layers to one bitmap
  val finalBitmap : Bitmap? = layeredPainterView.convertToBitmap() 

  // LayerManagement
  layeredPainterView.moveLayer(from,to)
  layeredPainterView.moveSelectedLayerDown()
  layeredPainterView.moveSelectedLayerUp()

  layeredPainterView.removeLayerAt(index)
  layeredPainterView.removeLayers(layersIndex: IntArray)

  val layerCount : Int = layeredPainterView.getLayerCount()
  val selectedLayerIndex : Int = layeredPainterView.getSelectedLayerIndex(): Int
  // Sets the layer at index to be the active selected layer.
  layeredPainterView.selectLayer(index: Int)

  // Sets the layer provided to be the active selected layer.
  layeredPainterView.selectLayer(paintLayer: PaintLayer)

  // Merge layers
  layeredPainterView.mergeLayers(layersIndex: IntArray)

  // Duplicate layers
  layeredPainterView.duplicateLayerAt(index)

  // Get list of `PaintLayer`s inside layeredPainterView
  val layers : List<PaintLayer> = layeredPainterView.getPaintLayers()

  // Clear layers and history
  layeredPainterView.clearLayers()
    
  // Change clip state with state save. You can undo the clip changes.
  layeredPainterView.setClipRectWithStateSave(rect: Rect,initialClip: Rect,animate: Boolean = true) {
    // Invoked after animation is done. Invoked immediately if animate = false.
  }

  
  
```
</details>

### Setup

Add `PainterView` to your xml layout:

```xml
  <ir.simurgh.photolib.components.paint.view.LayeredPainterView
        android:id="@+id/paintView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:background="@color/gray"
        android:paddingHorizontal="16dp"
        />

  // OR

  <ir.simurgh.photolib.components.paint.view.PainterView
        android:id="@+id/backingPaintView"
        android:layout_width="0dp"
        android:layout_height="0dp" 
        android:background="@color/gray"
        android:paddingHorizontal="16dp"/>

  // Then find it.
  val painterView = findViewById(R.id.paintView)
  val painterView = binding.paintView

  painterView.addNewLayer(bitmap or PaintLayer)

```

### 🖌️ Brush Painter
<a href="https://imgbb.com/"><img src="https://i.ibb.co/4Rw361H3/Hue-Flow-ezgif-com-optimize.gif" alt="Hue-Flow-ezgif-com-optimize" border="0" /></a> <a href="https://imgbb.com/"><img src="https://i.ibb.co/SwQvVzF7/Brush-Painter-ezgif-com-optimize.gif" alt="Brush-Painter-ezgif-com-optimize" border="0" /></a>  <a href="https://imgbb.com/"><img src="https://i.ibb.co/tTBFCtRr/Speed-Sensitivitiy-ezgif-com-optimize.gif" alt="Speed-Sensitivitiy-ezgif-com-optimize" border="0" /></a> <a href="https://imgbb.com/"><img src="https://i.ibb.co/dwsNcW05/Eraser-ezgif-com-optimize.gif" alt="Eraser-ezgif-com-optimize" border="0"></a>

- **Various Brushes**: Render customizable brushes.
- **Texture Support**: Apply custom textures to brush strokes.
- **Alpha Blending**: Support for alpha-blending mode which controls blending of brushes alpha with target canvas.
- **Line Smoothing**: Customizable algorithms for smoothing lines.
- **Pressure Sensitivity**: Support for pressure-sensitive input for size and opacity of brush.

This `Painter` is a tool that enables drawing on layers. It takes a `DrawingEngine` and a `LineSmoother` object and manages the final layers for drawing.
It passes `TouchData` from `PainterViews` to `LineSmoother` and manages the points provided by `LineSmoother` and draws them with `DrawingEngine`.
It has a `brush` property that is responsible for rendering a stamp on provided points by `LineSmoother` and `DrawingEngine`.

- **`LineSmoother`**: Responsible for interpolating between touch points and providing final points to be drawn.
- **`DrawingEngine`**: Responsible for rendering a `Brush` with its properties on provided points.

**Brush Types Available:**
- **NativeBrush**: Solid color painting that supports rectangle and circle shape.
- **BitmapBrush**: Bitmap-based brush that draws a bitmap on provided point.
- **SpriteBrush**: Brush that renders a set of bitmaps in sequential or random order.
- **Custom Brushes**: Create yourown brushes by extending `Brush` class.

<details>
<summary>
  Brush Properties
</summary>


**Brush**
| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `size` | `Int` | `1` | Base size of the brush in pixels |
| `color` | `Int` | `Color.BLACK` | Primary color of the brush strokes |
| `opacity` | `Float` | `1f` | Base opacity of the brush (0.0 = transparent, 1.0 = opaque) |
| `spacing` | `Float` | `0.1f` | Spacing between brush stamps as fraction of brush size (0.1 = close together, 1.0 = far apart) |
| `scatter` | `Float` | `0f` | Random position offset for brush stamps |
| `angle` | `Float` | `0f` | Base rotation angle for brush stamps in degrees |
| `squish` | `Float` | `0f` | Brush squish factor (0.0 = normal, 1.0 = completely flattened vertically) |
| `smoothness` | `Float` | `0f` | Line smoothing amount (0.0 = no smoothing, higher values = more smoothing) |
| `angleJitter` | `Float` | `0f` | Random angle variation in degrees (0.0 = no jitter, 1.0 = full 360° random) |
| `sizeJitter` | `Float` | `0f` | Random size variation (0.0 = no jitter, 1.0 = up to 100% size variation) |
| `alphaBlend` | `Boolean` | `false` | Whether to use alpha blending mode for smoother opacity mixing |
| `autoRotate` | `Boolean` | `false` | Whether brush should automatically rotate based on stroke direction |
| `sizePressureSensitivity` | `Float` | `0.6f` | How much brush size responds to pressure changes (0.0 = no response, 1.0 = full response) |
| `minimumPressureSize` | `Float` | `0.3f` | Minimum size multiplier when pressure is at minimum |
| `maximumPressureSize` | `Float` | `1f` | Maximum size multiplier when pressure is at maximum |
| `isSizePressureSensitive` | `Boolean` | `false` | Whether brush size should respond to pressure changes |
| `opacityPressureSensitivity` | `Float` | `0.5f` | How much brush opacity responds to pressure changes |
| `minimumPressureOpacity` | `Float` | `0f` | Minimum opacity multiplier when pressure is at minimum |
| `maximumPressureOpacity` | `Float` | `1f` | Maximum opacity multiplier when pressure is at maximum |
| `isOpacityPressureSensitive` | `Boolean` | `false` | Whether brush opacity should respond to pressure changes |
| `opacityJitter` | `Float` | `0f` | Random opacity variation amount (0.0 = no jitter, 1.0 = full random opacity) |
| `opacityVariance` | `Float` | `0f` | Speed-based opacity variance (-1.0 to 1.0, negative decreases opacity with speed) |
| `opacityVarianceSpeed` | `Float` | `0.6f` | Speed at which opacity variance responds to movement changes |
| `opacityVarianceEasing` | `Float` | `0.1f` | How quickly opacity variance transitions smooth out (lower = smoother) |
| `sizeVariance` | `Float` | `1f` | Speed-based size variance (1.0 = no change, <1.0 decreases with speed, >1.0 increases) |
| `sizeVarianceSensitivity` | `Float` | `0.1f` | How sensitive size variance is to movement speed changes |
| `sizeVarianceEasing` | `Float` | `0.08f` | How quickly size variance transitions smooth out |
| `hueJitter` | `Int` | `0` | Random hue shift amount in degrees (0 = no shift, 360 = full spectrum) |
| `hueFlow` | `Float` | `0f` | Speed of hue color cycling (0.0 = no flow, higher = faster cycling) |
| `hueDistance` | `Int` | `0` | Maximum hue shift distance in degrees for hue flow oscillation |
| `startTaperSpeed` | `Float` | `0.03f` | Speed at which stroke tapers in at the beginning |
| `startTaperSize` | `Float` | `1f` | Initial size multiplier for stroke tapering (< 1.0 = starts small, > 1.0 = starts large) |
| `startTaperSize` | `Float` | `1f` | Initial size multiplier for stroke tapering (< 1.0 = starts small, > 1.0 = starts large) |
| `texture` | `Bitmap?` | `null` | Optional texture bitmap to apply to brush strokes |
| `textureTransformation` | `Matrix?` | `null` | Transformation matrix for texture positioning and scaling |

**NativeBrush**
| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `softness` | `Float` | `0.2f` | Softness of the brush edges (0.0 = hard edges, 1.0 = very soft/feathered edges) |

**BitmapBrush**
| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `brushBitmap` | `Bitmap` | `null` | Bitmap used for stamping |

| Method | Description |
|--------|-------------|
| `changeBrushBitmap(newBitmap: Bitmap?, recycleCurrentBitmap: Boolean)` | Changes the bitmap used for this brush |

**SpirteBrush**
| Method | Description |
|--------|-------------|
| `changeBrushes(newBitmaps: List<Bitmap>?, recycleCurrentBitmaps: Boolean)` | Changes the list of bitmaps to be drawn |

</details>

### 🎟️ Default Brush Stamps
There is a number of brush stamp and texture resources available at Simurgh's module at `R.drawable`.
These brushes were provided by `myphotoshopbrushes.com`.

```
// Stamps
R.drawable.free_charcoal_bruses_10
R.drawable.free_charcoal_bruses_2
R.drawable.free_charcoal_bruses_7
R.drawable.hair
R.drawable.real_6
R.drawable.wavy_hair

// Textures
R.drawable.back_pattern
R.drawable.basketball
R.drawable.binding
R.drawable.gray_lines
R.drawable.knitted_netting
R.drawable.large_leather
R.drawable.paper

```

### Setup

```kotlin
  val brushPainter = BrushPainter(CanvasDrawingEngine())
  brushPainter.brush = NativeBrush(size = 24, color = Color.RED)

  painterView.painter = brushPainter
  // READY TO DRAW!

  // Enable eraser mode
  brushPainter.setEraserMode(isEnabled: Boolean)

  // Set texture on brush strokes
  brushPainter.changeBrushTextureBlending(blendMode: PorterDuff.Mode)

```
### 🖌️ BrushPreview

<a href="https://imgbb.com/"><img src="https://i.ibb.co/SwkN7FHs/Brush-Preview-ezgif-com-optimize.gif" alt="Brush-Preview-ezgif-com-optimize" border="0"></a>

`BrushPreview` is a custom View component that renders a preview of how a `Brush` will look when drawing.
It displays a sample stroke using the configured brush properties and settings.

<details>
  <summary>
    BrushPreview members
  </summary>

```kotlin

// Controls whether the checkerboard background pattern is displayed behind the brush preview.
brushPreview.isCheckerBoardEnabled = true

// The brush instance used to render the preview stroke.
brushPreview.brush = myBrush

// Requests a re-render of the brush preview. Recalculates points and triggers a view invalidation.
brushPreview.requestRender()

// Creates a bitmap snapshot of a brush stroke preview. Generates a bitmap showing how the brush will appear when drawing.
val snapshot = BrushPreview.createBrushSnapshot(
    targetWidth = 200,
    targetHeight = 100,
    paddingHorizontal = 10f,
    paddingVertical = 5f,
    brush = myBrush,
    resolution = 320,
    lineSmoother = BezierLineSmoother(),
    customPath = null
)
```
</details>

### SETUP
Add `BrushPreview` to your xml layout.

```xml
 <ir.simurgh.photolib.components.paint.painters.painting.BrushPreview
            android:id="@+id/brushPreview"
            android:layout_width="match_parent"
            android:layout_height="120dp"
            android:paddingHorizontal="16dp"
             />

  // Then find it. 
  val brushPreview = findViewById(R.id.brushPreview)
  // Set the brush you want to preview.
  brushPreview.brush = NativeBrush()
```

### 📐 Transform Tool
<a href="https://imgbb.com/"><img src="https://i.ibb.co/5hyyjWPQ/Bitmap-Transformable-ezgif-com-optimize-1.gif" alt="Bitmap-Transformable-ezgif-com-optimize-1" border="0" /></a> <a href="https://imgbb.com/"><img src="https://i.ibb.co/d44xF8cD/Text-Transformable-ezgif-com-optimize.gif" alt="Text-Transformable-ezgif-com-optimize" border="0" /></a> <a href="https://imgbb.com/"><img src="https://i.ibb.co/3mhwr0Sz/Perspective-ezgif-com-optimize.gif" alt="Perspective-ezgif-com-optimize" border="0" /></a> <a href="https://imgbb.com/"><img src="https://i.ibb.co/YFF6h7dZ/Guidelines-ezgif-com-optimize.gif" alt="Guidelines-ezgif-com-optimize" border="0"></a> <a href="https://imgbb.com/"><img src="https://i.ibb.co/hxxxxPT2/Texture-ezgif-com-optimize-1.gif" alt="Texture-ezgif-com-optimize-1" border="0"></a>
<a href="https://imgbb.com/"><img src="https://i.ibb.co/RGnzcbXk/Gradient-ezgif-com-optimize.gif" alt="Gradient-ezgif-com-optimize" border="0"></a>

- **Multi-touch Gestures**: Scale, rotate, and translate objects with touch controls.
- **Free Transform Mode**: Advanced corner-by-corner manipulation for perspective adjustments.
- **Matrix Transformations**: Full matrix-based transformation support.
- **Guidelines**: Full rotation and alignment guidelines for precise positioning.
- **Undo/Redo Support**: Complete history tracking with stack-based management.

This Painter is a tool that enables transformation and manipulation of objects on layers. It takes `Transformable` objects such as `TextTransformable`, `BitmapTransformable`, `ShapeTransforamble`.
It can have multiple `Transformable` objects at the same time.
It takes a` HandleTransformer`, `SmartAlignmentGuidelineDetector`, and `SmartRotationGuidelineDetector` and manages `Transformable` objects for interactive editing.
It passes `TouchData` from `PainterViews` to `HandleTransformer` and manages the transformation matrices applied to `Transformable` objects, rendering them with visual feedback.
It has handleDrawable and bound properties that are responsible for rendering transformation handles and bounds around selected objects, along with smart guidelines for precise positioning.

- **`HandleTransformer`**: Responsible for logic behind transforming `Transformable` objects with handles.
- **`SmartAlignmentGuidelineDetector`**: Responsible for finding guidelines for alignments of `Transformable` objects.
- **`SmartRotationGuidelineDetector`**: Responsible for finding guidelines for rotation of `Transformable` objects.

> [!WARNING]
> Custom shaped shadows and some blending modes are not hardware accelerated on API below version 28 (P). Enable `LAYER_TYPE_SOFTWARE` on `PainterView` to
> correctly show custom shaped shadows and blending modes.


<details>
  <summary>
  Transformables
</summary>

### 💬 TextTransformable
`Transformable` object responsible for rendering text. It supports textures, gradients, letter spacing, line spacing, stroke, customizable background, underline, shadow, etc.

```kotlin
// Text content to be rendered.
textTransformable.text = "Simurgh"

// Color of the text.
textTransformable.textColor = Color.RED

// Changes the text color (Colorable interface method.)
textTransformable.changeColor(color: Int)

// Gets the current text color (Colorable interface method.)
val color: Int = textTransformable.getColor()

// Line spacing between text lines.
textTransformable.lineSpacing = 24f

// Letter spacing for text rendering (Persian is not supported yet.)
textTransformable.letterSpacing = 34f

// Text alignment setting.
textTransformable.alignmentText = Alignment.CENTER

// Typeface used for text rendering.
textTransformable.typeface = Typeface.DEFAULT

// Current typeface style (normal, bold, italic, etc.)
textTrasnformable.typefaceStyle = Typeface.NORMAL

// Size of underline decoration.
textTransformable.underlineSize = 0f

// Flag indicating whether strikethrough decoration is enabled.
textTransformable.isStrikethrough = false

// Sets the typeface and style for text rendering.
textTransformable.setTypeface(typeface: Typeface, style: Int)

// Sets the text style using the current typeface.
textTransformable.setTextStyle(style: Int)

// Applies a bitmap texture to the text with default tile mode.
textTransformable.applyTexture(bitmap: Bitmap)

// Applies a bitmap texture to the text with specified tile mode.
textTransformable.applyTexture(bitmap: Bitmap, tileMode: Shader.TileMode)

// Removes the currently applied texture.
textTransformable.removeTexture()

// Gets the currently applied texture bitmap.
val textureBitmap : Bitmap = textTransformable.getTexture()

// Shifts the gradient/texture position by the specified offset.
textTransformable.shiftColor(dx: Float, dy: Float)

// Scales the gradient/texture by the specified factor.
textTransformable.scaleColor(scaleFactor: Float)

// Rotates the gradient/texture by the specified angle.
textTransformable.rotateColor(rotation: Float)

// Resets all gradient/texture transformations to default state.
textTransformable.resetComplexColorMatrix()

// Concatenates the specified matrix with the current gradient/texture transformation matrix.
textTransformable.concatColorMatrix(matrix: Matrix)

// Removes any applied gradient effects.
textTransformable.removeGradient()

// Applies a linear gradient to the text.
textTransformable.applyLinearGradient(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        colors: IntArray,
        position: FloatArray?,
        tileMode: Shader.TileMode
    )

// Applies a radial gradient to the text.
textTransformable.applyRadialGradient(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colors: IntArray,
        stops: FloatArray?,
        tileMode: Shader.TileMode
    )

// Applies a sweep gradient to the text.
textTransformable.applySweepGradient(
        cx: Float,
        cy: Float,
        colors: IntArray,
        positions: FloatArray?
    )

// Checks if a gradient effect is currently applied to the text.
val isApplied = text.isGradientApplied()

// Reports the gradient color stop positions.
val stops : FloatArray? = textTransformable.reportPositions()

// Reports the gradient colors.
val colors: IntArray? = textTransformable.reportColors()

// Sets stroke properties for the text outline.
textTransformable.setStroke(strokeRadiusPx: Float, strokeColor: Int)

// Gets the current stroke color.
val outlineColor : Int = textTransformable.getStrokeColor()

// Gets the current stroke width.
val strokeWidth:Float = textTransformable.getStrokeWidth()

// Sets shadow properties for the text
textTransformable.setShadow(radius: Float, dx: Float, dy: Float, shadowColor: Int)

// Removes the shadow effect from the text.
textTransformable.clearShadow()

// Gets properties of shadow.
val shadowDeltaX: Float = textTransformable.getShadowDx()
val shadowDeltaY: Float = textTransformable.getShadowDy()
val shadowRadius : Float = textTransformable.getShadowRadius()
val shadowColor : Int = textTransformable.getShadowColor()

// Converts the text to a bitmap with the specified configuration.
val textBitmap : Bitmap? = textTransformable.toBitmap(config: Bitmap.Config)

// Converts the text to a bitmap with specified dimensions and configuration.
val textBitmap : Bitmap? = textTransformable.toBitmap(width: Int, height: Int, config: Bitmap.Config)

// Sets the blend mode for text rendering.
textTransformable.setBlendMode(blendMode: PorterDuff.Mode)

// Clears the current blend mode and resets to default.
textTransformable.clearBlend()

// Gets the current blend mode.
val currentBlending : PorterDuff.Mode = textTransformable.getBlendMode()

// Set opacity of text.
textTransformable.setOpacity(opacity: Int)

// Get opacity of text
textTransformable.getOpacity()

// Sets the background properties for the text element.
textTransformable.setBackground(padding: Float, radius: Float, @ColorInt color: Int)

// Gets the current background padding value.
val backgroundPadding : Float = textTransformble.getBackgroundPadding()

// Gets the current background corner radius.
val backgroundRadius : Float = textTransformble.getBackgroundRadius()

// Gets the current background color.
val backgroundColor : Int = textTransformable.getBackgroundColor()

// Gets the current background enabled state.
// true if the text background is enabled, false otherwise.
val isBackgroundEnabled : Boolean = textTransformable.getBackgroundState()

// Sets whether the text background is enabled or disabled.
textTransformable.setBackgroundState(isEnabled: Boolean)

// Sets the unified state for the background rendering.
// Unified state affects how multiple background elements are rendered together.
textTransformable.setBackgroundUnifiedState(isUnified: Boolean)

// Checks if the background is in unified rendering mode.
textTransformable.isBackgroundUnified()

// Cloning
val clonedTransformable = textTransformable.clone()
```

### 🖼️ BitmapTransformable
`Transformable` object responsible for rendering bitmap. It supports stroke, image adjustment (brighntess, contrast, hue, etc.), customizable background, shadow, etc.

> All the methods and properties present in TextTransformable such as background, shadow, stroke, etc is persent in this class too.

```kotlin
// The source bitmap being painted
bitmapTransformable.bitmap = Bitmap()

// Container for image adjustment values including brightness, contrast, etc.
val adjustments : ImageAdjustment = bitmapTransformable.imageAdjustments

// Optional custom path for non-rectangular bitmap shapes (useful for custom shape outlines.)
val customPath : Path = BitmapPathConverter().bitmapToPath(bitmap: Bitmap, alphaThreshold: Int = 128) // This is heavy operation and should run on the background if possible.
bitmapTransformable.customBackgroundPath = customPath

// Sets the corner roundness for the bitmap.
bitmapTransformable.setCornerRoundness(roundness: Float)

// Gets the current corner roundness value.
val radius: Float bitmapTransformable.getCornerRoundness()

// Sets the hue adjustment for the bitmap.
bitmapTransformable.setHue(@FloatRange(0.0, 360.0) degree: Float)

// Sets the contrast adjustment for the bitmap.
bitmapTransformable.setContrast(@FloatRange(0.0, 2.0) contrast: Float)

// Sets the saturation adjustment for the bitmap.
bitmapTransformable.setSaturation(@FloatRange(0.0, 2.0) saturation: Float)

// Sets the brightness adjustment for the bitmap.
bitmapTransformable.setBrightness(@FloatRange(-1.0, 1.0) brightness: Float)

// Sets the tint adjustment for the bitmap.
bitmapTransformable.setTint(@FloatRange(-1.0, 1.0) tint: Float)

// Sets the temperature adjustment for the bitmap.
bitmapTransformable.setTemperature(@FloatRange(-1.0, 1.0) warmth: Float)

// Resets all image adjustments to their default values.
bitmapTransformable.resetAdjustments()

// Cloning
val clonedTransformable = bitmapTransformable.clone()
```

### 🔺 ShapeTransformable
`Transformable` object responsible for rendering a `SimurghShape`. It supports stroke, shadow, texture, gradient, etc.

> All the methods and properties present in TextTransformable such as background, shadow, stroke, gradient, texture, etc is persent in this class too.

```kotlin
shapeTransformable.shape = SimurghShape() // sub-classes of this class is supported.

// Cloning.
val clonedTransformable = shapeTransformable.clone()
```

</details>

<details>
<summary>
  TransformTool Methods
</summary>

```kotlin
// Adds a new transformable child object to the tool
transformTool.addChild(transformable: Transformable)

// a new transformable child object with a specific target rectangle.
transformTool.addChild(transformable: Transformable, targetRect: RectF?)

// Removes the currently selected child object from the tool.
transformTool.removeSelectedChild()

// Removes all child objects from the tool.
transformTool.removeAllChildren()

// Removes a child object at the specified index.
transformTool.removeChildAt(index: Int)

// Applies all child transformations to the current layer's bitmap.
transformTool.applyComponentOnLayer()

// Control width of the bounding box drawn around selected chil.
transformTool.boundStrokeWidth = 5f or dp(5) // Extension function for converting numbers to dp values, present inside the library.

// Control the color of bounding box.
transformTool.boundColor = Color.BLACK

// Drawable for handles drawn around selected child.
transformTool.handleDrawable = anyDrawable

// Get list of all children inside TransformTool.
val children : List<Transformable> = transformTool.children

// When true, prevents any transformation operations on objects, effectively locking them in place.
transformTool.isTransformationLocked = true

// Controls visibility of transformation bounds. When false, bounds are not drawn around selected objects.
transformTool.isBoundsEnabled = true

// Current selected child.
val selectedChild: Transformable? = transformTool.selectedChild

// SmartGuidelines stroke width.
transformTool.smartGuidelineStrokeWidth = 5f or dp(5)

// SmartGuideline stroke color.
transformTool.smartGuidelineColor = Color.MAGENTA

// Change the classes responsible for detecting guidelines.
transformTool.rotationGuidelineDetector = MyOwnDetector() // Default is DefaultRotationSmartGuidelineDetector()
transfomrTool.alignmentGuidelineDetector = MyOwnDetector() // Defualt is DefaultAlignmentSmartGuidelineDetector()

// Erase guidelines on the screen.
transfomrTool.eraseAlignmentSmartGuidelines()
transfomrTool.eraseRotationSmartGuidelines()

// Children arrangement.
transformTool.bringSelectedChildUp()
transformTool.bringSelectedChildToFront()
transformTool.bringSelectedChildDown()
transformTool.bringSelectedChildToBack()

// Applies a transformation matrix to the selected child object.
transformTool.applyMatrix(matrix: Matrix)

// Rotates the selected child object by the specified number of degrees.
transformTool.rotateSelectedChildBy(degree: Float)

// Resets the rotation of the selected child object to zero degrees.
transformTool.resetSelectedChildRotation()

// Flips the selected child object vertically (around horizontal axis).
transformTool.flipSelectedChildVertically()

// Flips the selected child object horizontally (around vertical axis).
transformTool.flipSelectedChildHorizontally()

// Resets the transformation matrix of the selected child object.
// This method can either reset to original bounds or to the current canvas bounds.
transformTool.resetSelectedChildMatrix(resetToBoundsRect: RectF? = null)

// Sets the transformation matrix of the selected child object.
transformTool.setMatrix(matrix: Matrix)

// Gets the transformation matrix of the selected child object.
val childMatrix : SimurghMatrix = transformTool.getChildMatrix()

// Gets the bounds of the selected child object after transformations.
// returns true if bounds were calculated (child is selected), false otherwise.
val isCalculationSuccessful = transformTool.getSelectedChildBounds(rect: RectF)

// Aligns the selected child object according to the specified alignment mode
// TOP, LEFT, RIGHT, BOTTOM, VERTICAL, HORIZONTAL, CENTER
transformTool.setSelectedChildAlignment(alignment: TransformableAlignment)

// Saves the current state of the selected child for undo functionality
transformTool.saveSelectedChildState()

// Undoes the last transformation operation
transformTool.undo()

// Redoes the next transformation operation.
transformTool.redo()

// Selection listeners

// Callback invoked when a child object is selected, providing access to the selected object and initialization state.
transformTool.onChildSelected = { transformable, isInInitializationPhase ->
}

// Callback invoked when a child object is deselected.
transformTool.onChildDeselected = {

}
```

</details>

### Setup
```kotlin
val transformTool = TransformTool(context)

// Add transformables.
transformTool.addChild(TextTransformable())
transformTool.addChild(BitmapTransformable())
transformTool.addChild(ShapeTransformable())

painterView.painter = transformTool

// HAPPY TRANSFORMING.
```

### ✂️ Crop Tool
<a href="https://imgbb.com/"><img src="https://i.ibb.co/99tS3VV1/Crop-Tool-ezgif-com-optimize.gif" alt="Crop-Tool-ezgif-com-optimize" border="0" /></a>
- **Smooth Animations**: Fluid transitions and adjustments.
- **Gesture Support**: Pinch, pan, and drag interactions.
- **Auto-fitting**: Keeps crop frame within image bounds.
- **Locked Ratios**: Support for common ratios (1:1, 4:3, 16:9, etc.)
- **Free-form Cropping**: Unconstrained aspect ratio mode.
- **Customizable Appearance**: Configurable colors and stroke widths for guidelines and handles.

`CropperTool` is a `Painter` that draws a visible area for cropping the content of layer. It takes an `AspectRatio` class and modify its frame based on it.
It offers smooth animations, aspect ratio controls, visual guidelines, and gesture-based manipulation.

<details>
  <summary>
    CropperTool members
  </summary>

```kotlin
// Color of the crop frame border.
cropperTool.frameColor = Color.DKGRAY

// Stroke width of the crop frame border.
cropperTool.frameStrokeWidth = context.dp(2)

// Stroke width of the rule-of-thirds guideline grid.
cropperTool.guidelineStrokeWidth = context.dp(1)

// Color of the rule-of-thirds guideline grid.
cropperTool.guidelineColor = Color.DKGRAY

// Determines if guideline should be drawn or not.
cropperTool.isDrawGuidelineEnabled = true

// Color of the darkened overlay area outside the crop frame.
cropperTool.backgroundShadowColor = Color.BLACK

// Alpha transparency level of the darkened overlay (0-255).
cropperTool.backgroundShadowAlpha = 85

// Stroke width of the corner and edge resize handles.
cropperTool.handleBarStrokeWidth = context.dp(3)

// Color of the corner and edge resize handles.
cropperTool.handleBarColor = Color.DKGRAY

// Shape of the handle bar endpoints (ROUND or SQUARE).
cropperTool.handleBarCornerType = Paint.Cap.ROUND

// Determines color of handle bars when they are selected. By default this value is same as handleBarColor.
cropperTool.selectedHandleBarColor = handleBarColor

// Returns the crop dimensions in the original image coordinate system. This accounts for all transformations applied to the canvas.
val dimensions = cropperTool.cropperDimensions

// Duration of crop frame animations in milliseconds.
cropperTool.animationDuration = 500L

// Interpolator for smooth crop frame animations.
cropperTool.animationInterpolator = FastOutSlowInInterpolator()

// Changes the aspect ratio constraint for the crop frame.
cropperTool.setAspectRatio(
    newAspectRatio : AspectRatio = aspectRatio,
    force = false
)

// Creates a new bitmap containing only the cropped portion of the image.
val croppedBitmap = cropperTool.crop()

// Clips the current layer to only show content within the crop frame. This modifies the existing layer bitmap directly.
cropperTool.clip()

// Sets the crop frame to a specific rectangle.
cropperTool.setFrame(
    rect = rect,
    fit = false,
    animate = true,
    onEnd = { }
)
```  
</details>

### Setup

```kotlin
val cropTool = CropperTool()

val lockedAspectRatio = AspectRatioLocked(16f,9f)
val freeAspectRatio = AspectRatioFree()

cropTool.setAspectRatio(lockedAspectRatio)

painterView.painter = cropTool

// When there is multiple layers in your layeredPainterView and you want to crop them all, consider
// Changing clip rect of layeredPainterView instead of actually cropping all the layers. This way you can benifit
// from performance and memory usage gains on top of non-destructive cropping.
// After adjusting crop frame and you are ready to crop you can do this:
paintView.setClipRectWithStateSave(
  Rect(cropperTool.cropperDimensions),
  initialClip = tempRect // could be layeredPainterView.identityClio or current clip of layeredPainterView,
  animate = true
)
```

### 🔍 ColorDropper
<a href="https://imgbb.com/"><img src="https://i.ibb.co/tTsKCXgh/Color-Dropper-ezgif-com-optimize.gif" alt="Color-Dropper-ezgif-com-optimize" border="0" /></a>
- **Magnified Preview Circle**: Shows enlarged pixels with custom maginification factor around the touch point.
- **Color Ring**: Displays the currently sampled color as a circular border.
- **Crosshair Indicator**: Precise pixel selection with automatic contrast adjustment.
- **Automatic Contrast**: Crosshair color adjusts based on background brightness.
- **Smart Positioning**: Automatically offsets the preview to avoid finger occlusion.

`ColorDropper` is a `Painter` that allows color sampling from layers. It displays a magnified circular preview of the pixels
around their finger, along with a colored ring showing the exact sampled color and a crosshair for precise selection.

<details>
  <summary>
    ColorDropper members
  </summary>

```kotlin
// Scale factor for magnifying the bitmap preview inside the circle.
// Controls how much the pixels are enlarged in the preview.
// Default value is 4.0f for 4x magnification.
colorDropper.magnifierScale = 3.0f

// Radius of the preview circle in pixels.
// Controls the size of both the magnified view and color ring.
colorDropper.circlesRadius = 120f

// Stroke width of the color ring in pixels.
colorDropper.colorRingStrokeWidth = circleRadius * 0.1f

// Color of the center crosshair.
// Automatically adjusts for contrast when set to white (default behavior).
colorDropper.centerCrossColor = Color.BLACK

// Stroke width of the center crosshair lines in pixels.
colorDropper.centerCrossStrokeWidth = 2f

// Length of the crosshair lines in pixels (not stroke width).
// Determines how far the crosshair extends from the center point.
colorDropper.centerCrossLineSize = 8f

// Sets a lambda callback for continuous color detection during touch movement.
colorDropper.setOnColorDetected { color ->
    // Handle real-time color changes
}

// Sets an interface callback for continuous color detection during touch movement.
colorDropper.setOnColorDetected(object : ColorDropper.OnColorDetected {
    override fun onColorDetected(color: Int) {
        // Handle color detection
    }
})

// Sets a lambda callback for final color selection when user lifts finger.
colorDropper.setOnLastColorDetected { color ->
    // Handle final color selection
}

// Sets an interface callback for final color selection when user lifts finger.
colorDropper.setOnLastColorDetected(object : ColorDropper.OnLastColorDetected {
    override fun onLastColorDetected(color: Int) {
        // Handle final color selection
    }
})
```
</details>

### Setup
```kotlin
val colorDropper = ColorDropper()

colorDropper.setOnLastColorDetected {
  // Do anything with the picked color.
}

painterView.painter = colorDropper
// HAPPY COLOR PICKING.
```

### 🤠 LassoTool
<a href="https://imgbb.com/"><img src="https://i.ibb.co/XrNDQy9y/Lasso-Tool-ezgif-com-optimize.gif" alt="Lasso-Tool-ezgif-com-optimize" border="0" /></a>
- **Visual Feedback**: Animated selection outline with marching ants effect.
- **Smooth Edges**: High-quality anti-aliased selection boundaries.
- **Multiple Operations**: Support for copy, cut, and clip operations on selections.

`Painter` responsible for drawing a lasso selection area for creating freehand selections on layers with support for copy/cut/clip the selected area.

<details>
  <summary>
    LassoTool members
  </summary>

```kotlin
// Clipper used inside LassoTool to perform copy/cut/clip operations.
lassoTool.clipper = YourownClipper() // Default is PathBitmapClipper()

// Creates a non-destructive copy of the selected area.
// Returns new bitmap containing only the selected content, or null if no valid selection.
val copiedBitmap = lassoTool.copy()

// Performs a cut operation that extracts the selected area.
// Creates a copy of the selection then removes it from the original image.
// Returns new bitmap containing the cut content, or null if no valid selection.
val cutBitmap = lassoTool.cut()

// Performs a destructive clip operation on the selected area.
// Removes the selected content from the original layer, leaving transparency.
lassoTool.clip()

  // Determines whether the selection is inverted (selecting outside the path).
// When true, everything outside the drawn path is selected instead of inside.
lassoTool.isInverse = false

// Resets the lasso tool to its initial state.
// Clears the current selection path and prepares for new selection.
lassoTool.reset()

// Retrieves the bounds of the current selection area.
lassoTool.getClippingBounds(rect)

// Releases resources and stops animations when the tool is no longer needed.
// Ensures proper cleanup to prevent memory leaks.
lassoTool.release()

// Undo the last operation.
lassoTool.undo()

// Redo the last undone operation.
lassoTool.redo()

```
</details>

### Setup
```kotlin
  // Clipper used inside LassoTool to perform copy/cut/clip operations.
  val clipper = PathBitmapClipper()
  // This brush is responsible for edge of selected areas. A line around the selection area would be drawn with this brush for smooth edges.
  // You can control the softness or shape of your selection edge with this brush.
  clipper.edgeBrush = NativeBrush(softness = 1f // Softer edges)
  val lassoTool = LassoTool(context,clipper)

  painterView.painter = lassoTool
  // GO COWBOY!
```

### 🤠 LassoColorPainter

<a href="https://imgbb.com/"><img src="https://i.ibb.co/nM4Ry94T/Lasso-Color-Painter-ezgif-com-optimize.gif" alt="Lasso-Color-Painter-ezgif-com-optimize" border="0" /></a>

`Painter` for creating lasso selections and filling them with solid colors.

<details>
  <summary>
    LassoColorPainter members
  </summary>

  ```kotlin
  // The color used to fill the lasso selection area.
lassoColorPainter.fillingColor = Color.BLACK
  ```
</details>

### Setup

```kotlin
  val lassoColorPainter = LassoColorPainter(context)
  lassoColorPainter.fillingColor = Color.MAGENTA
  painterView.painter = lassoColorPainter
```

### 🧺 FloodFill Patiner (Bucket Tool)

<a href="https://imgbb.com/"><img src="https://i.ibb.co/rGW88RPY/Flood-Fill-ezgif-com-optimize.gif" alt="Flood-Fill-ezgif-com-optimize" border="0" /></a>

`Painter` that handles touch events to trigger flood fill operations on bitmap layers.
The actual flood fill algorithm is delegated to external handlers via callback,
allowing for delgation to background thread for processing and ability to use different flood fill implementations.

For adding FloodFill algorithms implement the `FloodFill` interface. There is a `FloodFillScanline` present for use.

### SETUP
```kotlin
val floodFiller = FloodFillScanline()

floodFillPainter.setOnFloodFillRequest { layerBitmap : Bitmap, ex: Int, ey: Int,
  // This way it is easier to run this floodFilling algorithm in background.
  // Maybe call this inside your viewModel.
  floodFiller.fill(bitmap, ex, ey, replaceColor, threshold = 0.1f)
}

painterView.painter = floodFillPainter
```

### 🌓 MaskModifierTool

<a href="https://imgbb.com/"><img src="https://i.ibb.co/SLwVmFr/Mask-Modifier-Tool-ezgif-com-gif-maker.gif" alt="Mask-Modifier-Tool-ezgif-com-gif-maker" border="0" /></a> <a href="https://imgbb.com/"><img src="https://i.ibb.co/5gm2NdGR/Mask-Modifier-Tool2-ezgif-com-optimize.gif" alt="Mask-Modifier-Tool2-ezgif-com-optimize" border="0" /></a>

- Create masks using any painter tool (brushes, shapes, etc.)
- Support for additive and subtractive mask operations
- Colored overlay showing mask areas with adjustable opacity
- Full undo/redo support for all mask operations

`Painter` responsible for doing masking operations. It takes other `Painter` objects that modify the layer like `LassoColorPainter`, `BrushPainter`, etc.
It create a mask layer inside it and modify the mask layer using `maskTool` property. It supports inverting the mask layer and copy/cut/clip operations.

**MaskTools**
- **`LassoMaskPainterTool`**: Create masks with LassoTool.
- **`MaskShapeTool`**: Create masks using shapes.

<details>
  <summary>
    MaskModifierTool members
  </summary>

```kotlin

// The painter tool used for editing the mask.
maskModifierTool.maskTool = BrushPainter()

// Color used for the mask overlay visualization.
maskModifierTool.maskColor = Color.RED

// Opacity of the mask overlay (0-255).
maskModifierTool.maskOpacity = 128

// Inverts the current mask, making masked areas unmasked and vice versa.
maskModifierTool.invertMaskLayer()

// Returns the current mask bitmap.
val maskBitmap = maskModifierTool.getMaskLayer()

// Applies the mask to clip the selected layer.
maskModifierTool.clip(shouldSaveHistory = true)

// Cuts the masked region from the selected layer and returns it as a new bitmap.
val cutBitmap = maskModifierTool.cut(shouldSaveHistory = true)

// Creates a copy of the masked region without modifying the original layer.
val copiedBitmap = maskModifierTool.copy()
  ```
</details>

### SETUP
```kotlin
  val shapeMaskTool = MaskShapeTool(context,shape)
  val lassoMaskTool = LassoMaskPainterTool(context)

  val clipper = BitmapMaskClipper()

  val maskModifierTool = MaskModifierTool(clipper)
  maskModifierTool.maskTool = shapeMaskTool

  painterView.painter = maskModifierTool
  
  // HAPPY MASKING!
```

### 🎭 BitmapMaskModifierTool

<a href="https://imgbb.com/"><img src="https://i.ibb.co/LdwtBff1/Bitmap-Mask-Modifier-Tool-ezgif-com-optimize-1.gif" alt="Bitmap-Mask-Modifier-Tool-ezgif-com-optimize-1" border="0" /></a>

`Painter` that takes bitmap and a mask bitmap and shows the result of masking immediately during editing. It takes a `DrawingEngine` with brush.
It's a useful tool for times you want to fine-tune the masking operation that you just did, or for example when you have a blured bitmap and normal bitmap
and you want to blur the parts you want precisely.

<details>
<summary>
BitmapMaskModifierTool members		
</summary>

```kotlin
// The original source bitmap to be masked.
bitmapMaskModifierTool.bitmap = sourceImageBitmap

// The mask bitmap that defines which areas of the source image are visible.
bitmapMaskModifierTool.maskBitmap = someMaskBitmap

// The brush used for painting mask areas.
bitmapMaskModifierTool.brush = eraserBrush

// Line smoother for creating natural-looking brush strokes.
bitmapMaskModifierTool.lineSmoother = BezierLineSmoother()

// The drawing engine that handles brush stroke rendering.
bitmapMaskModifierTool.engine = drawingEngine

// Reset the tool state.
bitmapMaskModifierTool.reset()
```
</details>

### SETUP
```kotlin
val maskBitmap = layerBitmap.copy(config,isMutable = true)

// Set color of this bitmap to black, this way, all the layer is visible.
// Later we erase parts of this mask to show the sourceBitmap instead of selected layer's bitmap.
maskBitmap.eraseColor(Color.BLACK)

val bitmapMaskModifierTool = BitmapMaskModifierTool(sourceBitmap // For example blured bitmap, maskBitmap, engine = DrawingEngine // Could be CanvasDrawingEngine)

// Modify the mask in realtime using this brush. 
bitmapMaskModifierTool.brush = NativeBrush(size = 20, softness = 0.8f)

// Set eraser mode to true. You can also draw instead of earasing but in this example we are erasing the mask.
drawingEngine.setEraserMode(true)

painterView.painter = bitmapMaskModifierTool

// HAPPY MASKING, I guess.
```

### 🌈 GradientSlider

<a href="https://imgbb.com/"><img src="https://i.ibb.co/99j7LqyS/Gradient-Slider-ezgif-com-optimize.gif" alt="Gradient-Slider-ezgif-com-optimize" border="0" /></a>

A custom view that displays an interactive gradient slider with draggable color handles. It reports the desired stops and colors for 
Linear,Radial and Sweep gradient type.
Users can tap to add new color stops, drag existing handles to reposition them,
and long-press to remove handles (minimum 2 handles must remain).

 <details>
   <summary>
     GradientSlider members
   </summary>

   ```kotlin
// Width of the gradient line in pixels.
gradientSlider.gradientLineWidth = 8f

// Stroke width for the color circle handles in pixels.
gradientSlider.colorCircleStrokeWidth = 1f

// Radius of the color circle handles in pixels.
gradientSlider.colorCircleRadius = 8f

// Color of the stroke around circle handles.
gradientSlider.circlesStrokeColor = Color.BLACK

// Touch area radius for circle handle interaction in pixels.
gradientSlider.circleHandleTouchArea = 24f

// Set a lambda callback for circle click events.
gradientSlider.setOnCircleClickedListener { index ->
    // Handle circle click at index
}

// Set a lambda callback for colors and positions change events.
gradientSlider.setOnColorsAndPositionsChangedListener { colors, positions ->
    // Handle colors and positions change
}

// Set a callback for when color or position changes have ended.
gradientSlider.setOnColorOrPositionsChangeEnded {
    // Handle when changes have ended
}

// Change the color of a circle handle at the specified index.
gradientSlider.changeColorOfCircleAt(Color.RED, 0)

// Change the color of the currently selected circle handle.
gradientSlider.changeColorOfSelectedCircle(Color.BLUE)

// Get the current colors of all circle handles in sorted order.
val currentColors = gradientSlider.getCurrentColors()

// Get the current positions of all circle handles in sorted order.
val positions = gradientSlider.getPositions()

// Set the colors and positions of the gradient programmatically.
val colors = intArrayOf(Color.WHITE, Color.RED, Color.BLACK)
val positions = floatArrayOf(0f, 0.5f, 1f)
gradientSlider.setColorsAndPositions(colors, positions)

// Reset the slider to its initial state with default white-to-black gradient.
gradientSlider.resetSlider()
   ```
 </details>

### SETUP
Add the slider to your xml layout
 ```xml
 <ir.simurgh.photolib.utils.GradientSlider
   android:id="@+id/gradientSliderView"
   android:layout_width="match_parent"
   android:layout_height="wrap_content"
   android:layout_marginHorizontal="8dp"
   android:layout_marginTop="8dp" />
  ```

```kotlin
  // Find it.
  val gradientSlider = findViewById(R.id.gradientSliderView)
  // Report values to Gradientable objects.
   gradientChild.applyRadialGradient(
    childBounds.width() * 0.5f,
    childBounds.height() * 0.5f,
    childBounds.width() * 0.5f,
    gradientSlider.getCurrentColors(),
    gradientSlider.getPositions()
  )
  
```

### 🛠 Custom Painter

Create your own painting tools by extending the `Painter` base class:

```kotlin
class WatercolorPainter : Painter() {
    private lateinit var watercolorEngine: WatercolorEngine
    private var currentStroke: WatercolorStroke? = null
    
    override fun onMoveBegin(touchData: TouchData) {
        currentStroke = WatercolorStroke().apply {
            addPoint(touchData.ex, touchData.ey, touchData.pressure)
        }
        watercolorEngine.beginStroke(currentStroke!!)
    }
    
    override fun onMove(touchData: TouchData) {
        currentStroke?.addPoint(touchData.ex, touchData.ey, touchData.pressure)
        watercolorEngine.updateStroke(currentStroke!!)
        sendMessage(PainterMessage.INVALIDATE)
    }
    
    override fun onMoveEnded(touchData: TouchData) {
        watercolorEngine.finalizeStroke(currentStroke!!)
        sendMessage(PainterMessage.SAVE_HISTORY)
        currentStroke = null
    }
    
    override fun draw(canvas: Canvas) {
        watercolorEngine.drawPreview(canvas)
    }
}
```

**Painter Lifecycle:**

1. **Initialization**: `initialize()` - Set up matrices and bounds
2. **Touch Events**: `onMoveBegin()` → `onMove()` → `onMoveEnded()`. Single tap only calls `onMoveEnded()`
3. **Rendering**: `draw()` - Called every frame
4. **Layer Events**: `onLayerChanged()` - When active layer changes
5. **Cleanup**: `release()` - Clean up resources

## 🛠 Installation

Add jitpack in your settings.gradle.kts at the end of repositories:
```gradle
 dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		mavenCentral()
		maven { url = uri("https://jitpack.io") } // This
	}
}

```

Then add dependency to your `build.gradle`
```gradle
 implementation 'com.github.Mohammad3125:SimurghPhotoEditor:0.6.1'
```

## 🎯 Quick Start

### Basic Setup

```xml
<ir..photolib.components.paint.paintview.LayeredPaintView
    android:id="@+id/paintView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var paintView: LayeredPaintView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        paintView = findViewById(R.id.paintView)
        setupPaintView()
    }
    
    private fun setupPaintView() {
        // Load image
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.sample_image)
        paintView.addNewLayer(bitmap)
        
        // Set up brush painter
        val brushPainter = BrushPainter().apply {
            brush = NativeBrush(size = 40)
        }
        paintView.painter = brushPainter
        
        // Listen to layer changes
        paintView.setOnLayersChangedListener { layers, selectedIndex ->
            // Handle layer changes
            updateLayerList(layers, selectedIndex)
        }
    }
}
```

### Export Results

```kotlin
// Convert to single bitmap
val resultBitmap = paintView.convertToBitmap()

// Get individual layer bitmaps
val layerBitmaps = layeredPainterView.getLayersBitmap()

// Save to file
resultBitmap?.let { bitmap ->
    saveBitmapToFile(bitmap, "edited_image.jpg")
}
```

## 🐛 Troubleshooting

### Common Issues

1. **Slow performance with multiple layers**
   ```kotlin
   
   layeredPainterView.isCachingEnabled = true
   ```

## 📄 License

```
MIT License

Copyright (c) 2025 Mohammad Hossein Naderi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 🤝 Contributing
This project needs your help for bug-fixes and feature developement. Let's make something great together.
Let me know where did you use this library and what apps you created with it.
1. Fork the project
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

## 📞 Support

- 📧 Email: [mohammadhnaderi88@gmail.com]
- 🕊 Telegram: [MohammadHosseinNaderi](https://telegram.me/Mohammad3125)
- 🐛 Issues: [GitHub Issues](https://github.com/YourUsername//issues)

## 🏆 Acknowledgments
- Built with ❤️ for the Android development community
- Part of line-smoothing algorithm was inspired by [krzysztofzablocki's LineDrawing](https://github.com/krzysztofzablocki/LineDrawing?tab=readme-ov-file) project. Thank you ❤️
- Brush stamps in this library is provided by `www.myphotoshopbrushes.com`
### 📱 Apps built with Simurgh
#### [Baboomeh](https://cafebazaar.ir/app/ir.baboomeh.editor)
<a href="https://imgbb.com/"><img src="https://i.ibb.co/SXxKS4jQ/logo-baboomeh.png" alt="logo-baboomeh" border="0"></a>
