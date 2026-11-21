package com.textboxes.gui;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Slider over a real range [min, max] with optional step snapping. */
public class ValueSlider extends SliderWidget {
    private final String label;
    private final double min, max, step;
    private final DoubleFunction<String> format;
    private final DoubleConsumer setter;

    public ValueSlider(int x, int y, int width, int height, String label, double min, double max, double step,
                       double current, DoubleFunction<String> format, DoubleConsumer setter) {
        super(x, y, width, height, Text.empty(), 0.0);
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = format;
        this.setter = setter;
        setReal(current);
    }

    public double getReal() {
        double v = min + value * (max - min);
        if (step > 0) v = Math.round(v / step) * step;
        return Math.max(min, Math.min(max, v));
    }

    /** Moves the knob without firing the setter. */
    public void setReal(double v) {
        value = max > min ? Math.max(0.0, Math.min(1.0, (v - min) / (max - min))) : 0.0;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Text.literal(label + ": " + format.apply(getReal())));
    }

    @Override
    protected void applyValue() {
        setter.accept(getReal());
    }

    public static String fmtInt(double v) {
        return Integer.toString((int) Math.round(v));
    }

    public static String fmtPercent(double v) {
        return Math.round(v) + "%";
    }
}
