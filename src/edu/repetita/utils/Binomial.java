package edu.repetita.utils;

public class Binomial {
    private int n, k, l=1, m=0;
    private int[] lst;
    public Binomial(int n, int k) {
        this.n = n;
        this.k = k;
        lst = new int[k];
        if (k == 1) {
            l = 0;
        }
    }
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

    private void computeNext() {
        while (l >= 0) {
            if (l > m) {
                lst[l] = lst[l-1] + 1;
                m = l;
                if (l < k-1) {
                    l++;
                } else {
                    break;
                }
            }
            else if (l < m) {
                lst[l]++;
                m = l;
                if (lst[l] < n-k+l+1) {
                    l++;
                } else {
                    l--;
                }
            }
            else { // l == m == k-1
                if (lst[l]+1 < n) {
                    lst[l]++;
                    break;
                } else {
                    l--;
                }
            }
        }
    }

    public int[] next() {
        return lst;
    }

    public boolean hasNext() {
        computeNext();
        return l >= 0;
    }
}
