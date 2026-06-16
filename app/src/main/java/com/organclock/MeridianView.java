package com.organclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * A schematic front-facing body figure with one meridian traced on it.
 *
 * The figure has thick limbs so a channel drawn near a limb's edge clearly
 * reads as "thumb side" vs "little-finger side", etc. Channels that run on the
 * back of the body/limb are drawn dashed; front channels solid. Coordinates are
 * normalized (0..1) and approximate — this is a schematic, not an acupoint atlas.
 */
public class MeridianView extends View {

    private final float[] path;
    private final boolean dashed;
    private final int limbColor;

    private final Paint limb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint startDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint endDot = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Silhouette centerlines (normalized). Limbs are drawn as thick rounded lines.
    private static final float[] L_ARM = {0.37f, 0.19f, 0.325f, 0.35f, 0.30f, 0.53f};
    private static final float[] R_ARM = {0.63f, 0.19f, 0.675f, 0.35f, 0.70f, 0.53f};
    private static final float[] L_LEG = {0.45f, 0.50f, 0.44f, 0.72f, 0.43f, 0.95f};
    private static final float[] R_LEG = {0.55f, 0.50f, 0.56f, 0.72f, 0.57f, 0.95f};

    MeridianView(Context c, float[] path, int color, boolean dashed, int limbColor) {
        super(c);
        this.path = path;
        this.dashed = dashed;
        this.limbColor = limbColor;

        limb.setStyle(Paint.Style.STROKE);
        limb.setStrokeCap(Paint.Cap.ROUND);
        limb.setStrokeJoin(Paint.Join.ROUND);
        limb.setColor(limbColor);

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

        // --- Silhouette ---
        // Torso
        limb.setStrokeWidth(w * 0.17f);
        c.drawLine(0.5f * w, 0.18f * h, 0.5f * w, 0.50f * h, limb);
        // Head
        limb.setStyle(Paint.Style.FILL);
        c.drawCircle(0.5f * w, 0.085f * h, w * 0.062f, limb);
        limb.setStyle(Paint.Style.STROKE);
        // Limbs
        limb.setStrokeWidth(w * 0.05f);
        drawPoly(c, L_ARM, limb, w, h);
        drawPoly(c, R_ARM, limb, w, h);
        drawPoly(c, L_LEG, limb, w, h);
        drawPoly(c, R_LEG, limb, w, h);

        // --- Meridian ---
        float stroke = w * 0.022f;
        mer.setStrokeWidth(stroke);
        mer.setPathEffect(dashed ? new DashPathEffect(new float[]{w * 0.03f, w * 0.022f}, 0) : null);
        drawPoly(c, path, mer, w, h);

        // Start (hollow) and end (filled) markers show the direction of flow.
        startDot.setStrokeWidth(stroke * 0.8f);
        c.drawCircle(path[0] * w, path[1] * h, w * 0.016f, startDot);
        int n = path.length;
        c.drawCircle(path[n - 2] * w, path[n - 1] * h, w * 0.018f, endDot);
    }

    private void drawPoly(Canvas c, float[] pts, Paint p, float w, float h) {
        Path poly = new Path();
        poly.moveTo(pts[0] * w, pts[1] * h);
        for (int i = 2; i < pts.length; i += 2) {
            poly.lineTo(pts[i] * w, pts[i + 1] * h);
        }
        c.drawPath(poly, p);
    }
}
