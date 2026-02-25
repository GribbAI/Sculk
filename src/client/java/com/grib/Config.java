package com.grib.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Config {
    public static boolean enabled = true;

    public static boolean drawLines = true;
    public static boolean drawBoxes = true;
    public static boolean showNames = true;
    public static boolean pulseOutline = true;

    public static final Set<String> tracked = Collections.synchronizedSet(new HashSet<>());
    public static boolean autoTrackNearest = false;
    public static int nearestCount = 3;

    public static final int MAX_RENDER_DISTANCE = 400;
    public static final double FOCAL = 300.0;

    public enum Mode { ALL, TRACKED_ONLY, NEAREST }
    public static Mode displayMode = Mode.ALL;
}
