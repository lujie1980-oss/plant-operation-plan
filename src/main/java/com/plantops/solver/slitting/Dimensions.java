package com.plantops.solver.slitting;

public record Dimensions(double widthMm, double lengthMm, double thicknessMm) {

    public double area() {
        return widthMm * lengthMm;
    }
}
