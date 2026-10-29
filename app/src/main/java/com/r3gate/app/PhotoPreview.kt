package com.r3gate.app

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.view.*
import android.widget.ImageView

object PhotoPreview {
    fun decode(context:Context,uri:Uri,maxSize:Int=1800):Bitmap {
        val resolver=context.contentResolver
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,bounds) }
        var sample=1
        while(bounds.outWidth/sample>maxSize || bounds.outHeight/sample>maxSize) sample*=2
        val bitmap=resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) } ?: throw java.io.FileNotFoundException()
        val orientation=runCatching { resolver.openInputStream(uri)!!.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) } }.getOrDefault(1)
        val m=Matrix()
        when(orientation) {
            2 -> m.setScale(-1f,1f)
            3 -> m.setRotate(180f)
            4 -> m.setScale(1f,-1f)
            5 -> { m.setRotate(90f); m.postScale(-1f,1f) }
            6 -> m.setRotate(90f)
            7 -> { m.setRotate(-90f); m.postScale(-1f,1f) }
            8 -> m.setRotate(-90f)
        }
        return if(m.isIdentity) bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,m,true).also { if(it!==bitmap) bitmap.recycle() }
    }
}

/** Pinch, double tap and pan affect the preview only; the original file is never rewritten. */
class ZoomPhoto(context:Context,bitmap:Bitmap):ImageView(context) {
    private var zoom=1f; private var x=0f; private var y=0f; private var lastX=0f; private var lastY=0f
    private val scale=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector:ScaleGestureDetector):Boolean { zoom=(zoom*detector.scaleFactor).coerceIn(1f,8f); applyTransform(); return true }
    })
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e:MotionEvent)=true
        override fun onDoubleTap(e:MotionEvent):Boolean { zoom=if(zoom>1f) 1f else 3f; x=0f; y=0f; applyTransform(); return true }
    })
    init { setImageBitmap(bitmap); scaleType=ScaleType.FIT_CENTER; contentDescription="Zdjęcie — powiększ dwoma palcami" }
    private fun applyTransform() { scaleX=zoom; scaleY=zoom; translationX=x; translationY=y; if(zoom==1f) { x=0f;y=0f;translationX=0f;translationY=0f } }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        scale.onTouchEvent(e); gestures.onTouchEvent(e)
        if(e.actionMasked==MotionEvent.ACTION_DOWN) { lastX=e.rawX;lastY=e.rawY }
        if(e.actionMasked==MotionEvent.ACTION_MOVE && !scale.isInProgress && e.pointerCount==1 && zoom>1f) { x+=(e.rawX-lastX);y+=(e.rawY-lastY); applyTransform() }
        lastX=e.rawX;lastY=e.rawY
        if(e.actionMasked==MotionEvent.ACTION_UP) performClick()
        return true
    }
    override fun performClick():Boolean { super.performClick();return true }
}
