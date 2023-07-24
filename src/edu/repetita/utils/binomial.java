package edu.repetita.utils;

public class binomial {
    public static long computeBinomial(int n, int k) {
        if (n-k < k) {
            k = n-k;
        }
        long product = 1;
        for (int i = n; i > n-k; i--) {
            product *= i;
        }
        for (int i = 2; i <= k; i++) {
            product /= i;
        }
        return product;
    }

    public static
}
