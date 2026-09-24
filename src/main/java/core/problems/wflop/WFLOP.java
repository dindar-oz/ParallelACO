package core.problems.wflop;

import java.util.Arrays;

class WindFarm
{
    static double D0= 70;  // Turbine diameter
    static double C_t= 0.88; // Thrust Coefficient
    static double a; // 1-Sqrt(1-C_t)

    static double nu=0.4; // Turbine efficiency
    static double ro_air = 1.225; // Air density

    static double k;// Terrain coefficient

    public WindFarm() {
    }
}

public class WFLOP {

    static double D0= 70;  // Turbine diameter
    static double C_t= 0.88; // Thrust Coefficient
    static double a; // 1-Sqrt(1-C_t)

    static double nu=0.4; // Turbine efficiency
    static double ro_air = 1.225; // Air density

    static double k;// Terrain Roughness

    static double wakeDiameter(double d0, double x)
    {
        return d0+2*k*x;
    }
    static double windDeficit(double d0, double v0, double x)
    {
        double dx = wakeDiameter(d0,x);
        return a* ( d0*d0 / (dx*dx));
    }

    static double cummulativeDeficit(double d0, double v0, double xs[])
    {
        double deficit_sq = Arrays.stream(xs).map((x)->windDeficit(d0,v0,x)).map(x->x*x).sum();

        return Math.sqrt(deficit_sq);
    }







}
