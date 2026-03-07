package fixtures.java;

public enum Color {
    RED, GREEN, BLUE;

    public String display() {
        return name().toLowerCase();
    }
}
