package com.leotv.launcher;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Original App Store mark from https://www.apple.com/app-store/; sized for a tvOS landscape tile. */
final class AppStoreBanner extends Drawable {
    private final Bitmap icon;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect source;
    private final RectF destination = new RectF();
    AppStoreBanner(Context context) {
        icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.official_app_store);
        source = new Rect(3, 3, icon.getWidth()-3, icon.getHeight()-3);
        mark.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                0,0,0,0,255, 0,0,0,0,255, 0,0,0,0,255, 2,0,0,0,-255
        })));
    }
    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        fill.setShader(new LinearGradient(0,b.top,0,b.bottom,
                new int[]{0xFF23BDF0,0xFF1779DB,0xFF20239D},new float[]{0f,.46f,1f},Shader.TileMode.CLAMP));
        canvas.drawRect(b,fill);
        float size = b.height()*1.14f;
        destination.set(b.exactCenterX()-size/2,b.exactCenterY()-size/2,
                b.exactCenterX()+size/2,b.exactCenterY()+size/2);
        canvas.drawBitmap(icon,source,destination,mark);
    }
    @Override public int getIntrinsicWidth() { return 500; }
    @Override public int getIntrinsicHeight() { return 300; }
    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); mark.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
