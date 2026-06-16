package com.organclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * A body figure with one meridian traced on it.
 *
 * The figure is a public-domain (CC0) full-body silhouette (Open Clip Art,
 * "pitr", via Wikimedia Commons). Its outline path ships in assets/body_path.txt
 * and is parsed into an android Path here (the platform vector loader can't
 * handle a path this large). The meridian routes are supplied as normalized
 * (0..1) points and mapped onto the centered figure; back channels are dashed.
 *
 * The pathways are approximate (schematic), not an acupoint atlas.
 */
public class MeridianView extends View {

    // Native viewport of the silhouette (keeps the body's aspect ratio).
    private static final float BODY_W = 165.17456f;
    private static final float BODY_H = 500.8457f;

    // Temporary calibration grid: set false once routes are encoded.
    private static final boolean DEBUG_GRID = true;

    private static Path sBody; // parsed once, reused

    private final float[] path;
    private final boolean dashed;

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint startDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint endDot = new Paint(Paint.ANTI_ALIAS_FLAG);

    MeridianView(Context c, float[] path, int color, boolean dashed, int limbColor) {
        super(c);
        this.path = path;
        this.dashed = dashed;

        if (sBody == null) {
            sBody = parseBody(c);
        }

        bodyPaint.setStyle(Paint.Style.FILL);
        bodyPaint.setColor(limbColor);

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

        if (sBody != null) {
            c.save();
            c.translate(bodyLeft, 0);
            c.scale(bodyW / BODY_W, h / BODY_H);
            c.drawPath(sBody, bodyPaint);
            c.restore();
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

        if (DEBUG_GRID) {
            drawGrid(c, w, h, bodyLeft, bodyW);
        }
    }

    private void drawGrid(Canvas c, float w, float h, float bodyLeft, float bodyW) {
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setColor(0x66FF5252);
        g.setStrokeWidth(1f);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(0xCCFF5252);
        t.setTextSize(w * 0.030f);
        for (int i = 0; i <= 10; i++) {
            float f = i / 10f;
            float x = bodyLeft + f * bodyW;
            c.drawLine(x, 0, x, h, g);
            c.drawText(String.valueOf(f), x + 2, h * 0.03f, t);
            float y = f * h;
            c.drawLine(bodyLeft, y, bodyLeft + bodyW, y, g);
            c.drawText(String.valueOf(f), bodyLeft + 2, y - 2, t);
        }
    }

    /** Read assets/body_path.txt and parse its M/L/C/Z commands into a Path. */
    private static Path parseBody(Context c) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getAssets().open("body_path.txt")));
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append(' ');
            }
            r.close();
            return parsePath(sb.toString());
        } catch (Exception e) {
            return null; // degrade gracefully: meridian without the figure
        }
    }

    // Minimal SVG-path parser supporting M/m, L/l, C/c, Z/z (all this file uses).
    private static Path parsePath(String d) {
        Path p = new Path();
        char[] s = d.toCharArray();
        int len = s.length;
        int[] idx = {0};
        char cmd = 0;
        float cx = 0, cy = 0, sx = 0, sy = 0;
        try {
            while (true) {
                skipSep(s, idx, len);
                if (idx[0] >= len) {
                    break;
                }
                char ch = s[idx[0]];
                if (isAlpha(ch)) {
                    cmd = ch;
                    idx[0]++;
                    if (cmd == 'z' || cmd == 'Z') {
                        p.close();
                        cx = sx;
                        cy = sy;
                        continue;
                    }
                }
                boolean rel = Character.isLowerCase(cmd);
                char C = Character.toUpperCase(cmd);
                if (C == 'M') {
                    float x = num(s, idx, len), y = num(s, idx, len);
                    if (rel) { x += cx; y += cy; }
                    p.moveTo(x, y);
                    cx = x; cy = y; sx = x; sy = y;
                    cmd = rel ? 'l' : 'L';
                } else if (C == 'L') {
                    float x = num(s, idx, len), y = num(s, idx, len);
                    if (rel) { x += cx; y += cy; }
                    p.lineTo(x, y);
                    cx = x; cy = y;
                } else if (C == 'C') {
                    float x1 = num(s, idx, len), y1 = num(s, idx, len);
                    float x2 = num(s, idx, len), y2 = num(s, idx, len);
                    float x = num(s, idx, len), y = num(s, idx, len);
                    if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy; }
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    cx = x; cy = y;
                } else {
                    idx[0]++; // skip anything unexpected
                }
            }
        } catch (Exception e) {
            // return whatever parsed so far
        }
        return p;
    }

    private static void skipSep(char[] s, int[] idx, int len) {
        while (idx[0] < len) {
            char ch = s[idx[0]];
            if (ch == ' ' || ch == ',' || ch == '\t' || ch == '\n' || ch == '\r') {
                idx[0]++;
            } else {
                break;
            }
        }
    }

    private static float num(char[] s, int[] idx, int len) {
        skipSep(s, idx, len);
        int start = idx[0];
        if (idx[0] < len && (s[idx[0]] == '-' || s[idx[0]] == '+')) {
            idx[0]++;
        }
        while (idx[0] < len) {
            char ch = s[idx[0]];
            if ((ch >= '0' && ch <= '9') || ch == '.') {
                idx[0]++;
            } else {
                break;
            }
        }
        return Float.parseFloat(new String(s, start, idx[0] - start));
    }

    private static boolean isAlpha(char ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
    }
}
