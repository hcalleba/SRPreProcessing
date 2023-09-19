package edu.repetita.solvers.sr.srpp;

public enum DEBUG {
    NONE,  // Nothing written
    FILE,  // Gurobi logs written to out/gurobi.log
    CONSOLE,  // Gurobi logs written to console
    MODEL,  // Whole model written to out/model.lp
}
