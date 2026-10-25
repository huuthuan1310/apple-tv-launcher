package com.leotv.launcher;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Monochrome weather pictograms in the Apple Weather visual style, drawn natively at any scale. */
final class WeatherGlyph extends Drawable {
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int code, size;
    private final boolean day;
    WeatherGlyph(float density,int code,boolean day) {
        this.code=code; this.day=day; size=Math.round(20*density);
        ink.setColor(Color.WHITE); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(1.6f);
        ink.setStrokeCap(Paint.Cap.ROUND); ink.setStrokeJoin(Paint.Join.ROUND);
    }
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void draw(Canvas canvas) {
        Rect bounds=getBounds(); int save=canvas.save();
        canvas.translate(bounds.left,bounds.top); canvas.scale(bounds.width()/24f,bounds.height()/24f);
        if(code<0) { canvas.drawCircle(12,12,7,ink); canvas.drawLine(12,8,12,13,ink); canvas.drawPoint(12,16,ink); }
        else if(code<=1 && !day) {
            Path moon=new Path();moon.moveTo(16,3);moon.cubicTo(7,2,3,11,8,18);
            moon.cubicTo(12,23,20,20,22,15);moon.cubicTo(14,18,10,10,16,3);canvas.drawPath(moon,ink);
        } else {
            if(code<=2) {
                float cx=code==2?8:12,cy=code==2?8:12;
                canvas.drawCircle(cx,cy,3.4f,ink);
                for(int i=0;i<8;i++) {
                    double angle=i*Math.PI/4;
                    canvas.drawLine(cx+(float)Math.cos(angle)*5.5f,cy+(float)Math.sin(angle)*5.5f,
                            cx+(float)Math.cos(angle)*7f,cy+(float)Math.sin(angle)*7f,ink);
                }
            }
            if(code>=2) {
                Path cloud=new Path();cloud.moveTo(6,16);cloud.cubicTo(0,16,1,9,6,10);
                cloud.cubicTo(7,3,17,3,18,10);cloud.cubicTo(24,9,25,16,19,16);cloud.close();
                ink.setStyle(Paint.Style.FILL);ink.setColor(0xFF7E8B98);canvas.drawPath(cloud,ink);
                ink.setStyle(Paint.Style.STROKE);ink.setColor(Color.WHITE);canvas.drawPath(cloud,ink);
                if(code>=95) { Path bolt=new Path();bolt.moveTo(13,16);bolt.lineTo(10,20);bolt.lineTo(14,20);bolt.lineTo(12,24);canvas.drawPath(bolt,ink); }
                else if(code>=71 && code<=77 || code==85 || code==86) {canvas.drawPoint(7,20,ink);canvas.drawPoint(12,22,ink);canvas.drawPoint(17,20,ink);}
                else if(code>=51) {for(int x=7;x<=18;x+=5) canvas.drawLine(x,19,x-1,22,ink);}
                else if(code==45 || code==48) {canvas.drawLine(4,19,20,19,ink);canvas.drawLine(7,22,17,22,ink);}
            }
        }
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){ink.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){ink.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
