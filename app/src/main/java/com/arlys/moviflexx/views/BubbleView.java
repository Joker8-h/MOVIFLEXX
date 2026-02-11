package com.arlys.moviflexx.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

public class BubbleView extends View {

    private class Drop {
        float x, y, radius;
        float speed;
    }

    private final ArrayList<Drop> drops = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    public BubbleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setColor(0x556FD7C6); // verde pasto turco translúcido
        paint.setStyle(Paint.Style.FILL);
        createDrops();
    }

    private void createDrops() {
        drops.clear();
        for (int i = 0; i < 35; i++) {
            Drop d = new Drop();
            d.radius = random.nextInt(9) + 6;     // pequeñas
            d.x = random.nextInt(1000);
            d.y = random.nextInt(1600);
            d.speed = random.nextFloat() * 6f + 1.9f; // velocidad suave
            drops.add(d);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (Drop d : drops) {
            d.y += d.speed;

            // cuando sale por abajo, reaparece arriba
            if (d.y > getHeight()) {
                d.y = -d.radius;
                d.x = random.nextInt(getWidth());
                d.speed = random.nextFloat() * 2f + 1.5f;
            }

            canvas.drawCircle(d.x, d.y, d.radius, paint);
        }

        invalidate(); // animación continua
    }
}
