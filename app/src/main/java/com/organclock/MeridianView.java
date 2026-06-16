package com.organclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * A body figure with one meridian traced on it.
 *
 * The figure is a public-domain (CC0) full-body silhouette (Open Clip Art,
 * "pitr", via Wikimedia Commons), shipped as the vector drawable ic_body. The
 * meridian routes are supplied as normalized (0..1) points and mapped onto the
 * centered figure; channels that run on the back are drawn dashed.
 *
 * The pathways are approximate (schematic), not an acupoint atlas.
 */
public class MeridianView extends View {

    // Native viewport of ic_body.xml (so the drawing keeps the body's aspect).
    private static final float BODY_W = 165.17456f;
    private static final float BODY_H = 500.8457f;

    private final float[] path;
    private final boolean dashed;
    private final int limbColor;
    private final Drawable body;

    private final Paint mer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint startDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint endDot = new Paint(Paint.ANTI_ALIAS_FLAG);

    MeridianView(Context c, float[] path, int color, boolean dashed, int limbColor) {
        super(c);
        this.path = path;
        this.dashed = dashed;
        this.limbColor = limbColor;

        body = c.getDrawable(R.drawable.ic_body);
        if (body != null) {
            body.mutate();
            body.setTint(limbColor);
        }

        mer.setStyle(Paint.Style.STROKE);
        mer.setStrokeCap(Paint.Cap.ROUND);
        mer.setStrokeJoin(Paint.Join.ROUND);
        mer.setColor(color);

        startDot.setStyle(Paint.Style.STROKE);
        startDot.setColor(color);
        endDot.setStyle(Paint.Style.FILL);
        endDot.setColor(color);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, Math.round(w * 1.7f));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float bodyW = h * (BODY_W / BODY_H);
        float bodyLeft = (w - bodyW) / 2f;

        if (body != null) {
            body.setBounds(Math.round(bodyLeft), 0, Math.round(bodyLeft + bodyW), Math.round(h));
            body.draw(c);
        }

        float stroke = w * 0.020f;
        mer.setStrokeWidth(stroke);
        mer.setPathEffect(dashed ? new DashPathEffect(new float[]{w * 0.03f, w * 0.022f}, 0) : null);

        Path poly = new Path();
        poly.moveTo(bodyLeft + path[0] * bodyW, path[1] * h);
        for (int i = 2; i < path.length; i += 2) {
            poly.lineTo(bodyLeft + path[i] * bodyW, path[i + 1] * h);
        }
        c.drawPath(poly, mer);

        startDot.setStrokeWidth(stroke * 0.8f);
        c.drawCircle(bodyLeft + path[0] * bodyW, path[1] * h, w * 0.015f, startDot);
        int n = path.length;
        c.drawCircle(bodyLeft + path[n - 2] * bodyW, path[n - 1] * h, w * 0.017f, endDot);
    }
}
