package dev.voidfps.surface;

/** Engine-independent block face. Adapter code maps this to/from Minecraft's own Direction. */
public enum Face {
    DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

    public static final Face[] VALUES = values();

    public final int dx, dy, dz;

    Face(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    /** 0 = X, 1 = Y, 2 = Z. */
    public int axis() {
        return dx != 0 ? 0 : dy != 0 ? 1 : 2;
    }

    /** +1 or -1 along {@link #axis()}. */
    public int sign() {
        return dx + dy + dz;
    }

    /** First tangent axis of the face plane (0=X,1=Y,2=Z). Defines the "A" direction for AO sampling. */
    public int tangentA() {
        return axis() == 2 ? 0 : axis() == 1 ? 0 : 2;
    }

    /** Second tangent axis of the face plane. */
    public int tangentB() {
        return axis() == 1 ? 2 : 1;
    }

    public Face opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }
}
