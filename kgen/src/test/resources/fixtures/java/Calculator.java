package fixtures.java;

public class Calculator {
    private int value;

    public Calculator() {
        this.value = 0;
    }

    public Calculator(int initial) {
        this.value = initial;
    }

    public int add(int a, int b) {
        return a + b;
    }

    public static int multiply(int a, int b) {
        return a * b;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public static final int MAX_VALUE = 1000;
    public static final String NAME = "Calculator";

    @Deprecated
    public int oldMethod() {
        return -1;
    }
}
