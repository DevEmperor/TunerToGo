package net.devemperor.tunertogo;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SeekBar;

public class UnclickableSeekBar extends SeekBar {
    public UnclickableSeekBar(Context context) {
        super(context);
    }

    public UnclickableSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public UnclickableSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean performClick() {
        return true;
    }
}
