package com.errorerrorerror.iosliderexample;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.errorerrorerror.ioslider.IOSlider;
import com.errorerrorerror.iosliderexample.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    int corner;
    int stroke;

    float elevation;

    ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        corner = binding.slider.getCornerRadius();
        stroke = binding.slider.getStrokeWidth();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            elevation = binding.slider.getElevation();
        }

        binding.cornerValue.setText(String.valueOf(corner));
        binding.strokeValue.setText(String.valueOf(stroke));
        binding.elevation.setText(String.valueOf(elevation));

        binding.negativeCorner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                corner = Math.max(0, corner -= 10);
                dispatchCornerChanged(binding.slider, binding.cornerValue, corner);
            }
        });

        binding.positiveCorner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                corner += 10;
                dispatchCornerChanged(binding.slider, binding.cornerValue, corner);
            }
        });

        binding.negativeStroke.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stroke = Math.max(0, stroke -= 10);
                dispatchStroke(binding.slider, binding.strokeValue, stroke);
            }
        });

        binding.positiveStroke.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stroke += 10;
                dispatchStroke(binding.slider, binding.strokeValue, stroke);
            }
        });

        binding.negativeElevation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                elevation = Math.max(0, elevation -= 10);
                dispatchElevation(binding.slider, binding.elevation, elevation);
            }
        });

        binding.positiveElevation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                elevation += 10;
                dispatchElevation(binding.slider, binding.elevation, elevation);

            }
        });
    }

    static void dispatchCornerChanged(final IOSlider slider, final TextView view, int newCorner) {
        slider.setCornerRadius(newCorner);
        view.setText(String.valueOf(newCorner));
    }

    static void dispatchStroke(final IOSlider slider, final TextView view, int strokeWidth) {
        slider.setStrokeWidth(strokeWidth);
        view.setText(String.valueOf(strokeWidth));
    }

    static void dispatchElevation(final IOSlider slider, final TextView view, float elevation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            slider.setElevation(elevation);
        }
        view.setText(String.valueOf(elevation));
    }

}
