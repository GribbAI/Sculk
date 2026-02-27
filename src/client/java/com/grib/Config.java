package com.grib.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("sculkmodtracker.json");

    public static Set<String> tracked = Collections.synchronizedSet(new LinkedHashSet<>());
    public static boolean autoTrackNearest = false;
    public static int nearestCount = 3;
    public static boolean enabled = true;

    public static Mode displayMode = Mode.ALL;

    public enum Mode { ALL, TRACKED_ONLY, NEAREST }

    public static final int MAX_RENDER_DISTANCE = 400;
    public static final double FOCAL = 300.0;

    @Expose
    public Map<String, Profile> profiles = new LinkedHashMap<>(); // preserve insertion order

    @Expose
    public String activeProfile = "default";

    // persistent copies for backward-compatibility fields (these will be serialized)
    @Expose
    private Set<String> trackedPersistent = new LinkedHashSet<>();

    @Expose
    private boolean autoTrackNearestPersistent = false;

    @Expose
    private int nearestCountPersistent = 3;

    @Expose
    private boolean enabledPersistent = true;

    @Expose
    private Mode displayModePersistent = Mode.ALL;

    @Expose
    public long autosaveIntervalMs = 5000;

    private transient long lastSaveTime = 0L;
    private transient long lastAutosaveCheck = 0L;

    private static Config INSTANCE = null;

    public static synchronized void initDefaultsIfNeeded() {
        if (INSTANCE == null) {
            INSTANCE = new Config();
            if (INSTANCE.profiles.isEmpty()) {
                Profile p = Profile.defaultProfile();
                INSTANCE.profiles.put("default", p);
            } else if (!INSTANCE.profiles.containsKey(INSTANCE.activeProfile)) {
                INSTANCE.activeProfile = INSTANCE.profiles.keySet().iterator().next();
            }
            applyPersistentToStatic();
            wrapTrackedSet();
            Profile pf = profile();
            if (pf != null) pf.enabled = enabled;
        }
    }

    private static void wrapTrackedSet() {
        if (tracked == null) tracked = Collections.synchronizedSet(new LinkedHashSet<>());
        if (INSTANCE != null && INSTANCE.trackedPersistent != null) {
            tracked.clear();
            tracked.addAll(INSTANCE.trackedPersistent);
            tracked = Collections.synchronizedSet(new LinkedHashSet<>(tracked));
        }
    }

    private static void applyPersistentToStatic() {
        if (INSTANCE == null) return;
        if (INSTANCE.trackedPersistent != null) {
            tracked = Collections.synchronizedSet(new LinkedHashSet<>(INSTANCE.trackedPersistent));
        } else {
            tracked = Collections.synchronizedSet(new LinkedHashSet<>());
        }
        autoTrackNearest = INSTANCE.autoTrackNearestPersistent;
        nearestCount = INSTANCE.nearestCountPersistent;
        enabled = INSTANCE.enabledPersistent;
        displayMode = (INSTANCE.displayModePersistent == null) ? Mode.ALL : INSTANCE.displayModePersistent;
    }

    private static void applyStaticToPersistent() {
        if (INSTANCE == null) return;
        INSTANCE.trackedPersistent = new LinkedHashSet<>(tracked == null ? Collections.emptySet() : tracked);
        INSTANCE.autoTrackNearestPersistent = autoTrackNearest;
        INSTANCE.nearestCountPersistent = nearestCount;
        INSTANCE.enabledPersistent = enabled;
        INSTANCE.displayModePersistent = displayMode;
    }

    public static synchronized Config get() {
        initDefaultsIfNeeded();
        return INSTANCE;
    }

    public static synchronized Profile profile() {
        initDefaultsIfNeeded();
        Profile p = get().profiles.get(get().activeProfile);
        if (p == null) {
            p = Profile.defaultProfile();
            get().profiles.put(get().activeProfile, p);
        }
        return p;
    }

    public static synchronized void setActiveProfile(String name) {
        initDefaultsIfNeeded();
        if (get().profiles.containsKey(name)) {
            get().activeProfile = name;
            Profile p = profile();
            if (p != null) enabled = p.enabled;
            save();
        }
    }

    public static synchronized Set<String> listProfiles() {
        initDefaultsIfNeeded();
        return new LinkedHashSet<>(get().profiles.keySet());
    }

    public static synchronized void addProfile(String name, Profile p) {
        initDefaultsIfNeeded();
        get().profiles.put(name, p);
        get().activeProfile = name;
        enabled = p.enabled;
        save();
    }

    public static synchronized void removeProfile(String name) {
        initDefaultsIfNeeded();
        if (get().profiles.containsKey(name)) {
            get().profiles.remove(name);
            if (get().profiles.isEmpty()) {
                get().profiles.put("default", Profile.defaultProfile());
                get().activeProfile = "default";
            } else {
                get().activeProfile = get().profiles.keySet().iterator().next();
            }
            Profile p = profile();
            if (p != null) enabled = p.enabled;
            save();
        }
    }

    public static synchronized void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                    INSTANCE = GSON.fromJson(r, Config.class);
                    if (INSTANCE == null) INSTANCE = new Config();
                }
            } else {
                INSTANCE = new Config();
            }
        } catch (Exception e) {
            e.printStackTrace();
            INSTANCE = new Config();
        }
        initDefaultsIfNeeded();
        applyPersistentToStatic();
        wrapTrackedSet();

        Profile pf = profile();
        if (pf != null) pf.enabled = enabled;
        save();
    }

    public static synchronized void save() {
        try {
            initDefaultsIfNeeded();
            applyStaticToPersistent();
            Profile pf = profile();
            if (pf != null) pf.enabled = enabled;
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(INSTANCE, w);
            }
            INSTANCE.lastSaveTime = System.currentTimeMillis();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void tickAutosave() {
        initDefaultsIfNeeded();
        long now = System.currentTimeMillis();
        if (get().autosaveIntervalMs <= 0) return;
        if (now - get().lastAutosaveCheck < 500) return; // cheap throttle
        get().lastAutosaveCheck = now;
        if (now - get().lastSaveTime > get().autosaveIntervalMs) save();
    }

    public static class Profile {
        // visible features
        @Expose public boolean enabled = true;
        @Expose public boolean drawLines = true;
        @Expose public boolean drawBoxes = true;
        @Expose public boolean showNames = true;
        @Expose public boolean showDistance = true;
        @Expose public boolean pulseOutline = true;
        @Expose public boolean showHealthBar = true;

        // visual tunables
        @Expose public float hudScale = 1.0f;
        @Expose public float hudOpacity = 1.0f;
        @Expose public float pulseFrequency = 1.0f;
        @Expose public float pulseStrength = 0.35f;

        // rendering behaveor
        @Expose public int maxRenderDistance = 400;
        @Expose public double focal = 300.0;
        @Expose public boolean smoothFade = true;
        @Expose public float fadeSpeed = 4.0f;
        @Expose public boolean minimapEnabled = true;
        @Expose public int minimapRadius = 80;
        @Expose public int minimapRangeMeters = 80;
        @Expose public int maxEntitiesRendered = 200;

        // theme
        @Expose public Theme theme = Theme.STREAMER;

        public enum Theme {
            DARK,
            LIGHT,
            STREAMER,
            CUSTOM
        }

        @Expose public int colorBase = 0xFF66CCFF;
        @Expose public int colorOutline = 0xFF66FFFF;
        @Expose public int hostileColor = 0xFFCC6666;
        @Expose public int trackedColor = 0xFF33FF88;

        public static Profile defaultProfile() {
            Profile p = new Profile();
            p.enabled = true;
            p.drawLines = true;
            p.drawBoxes = true;
            p.showNames = true;
            p.showDistance = true;
            p.pulseOutline = true;
            p.hudScale = 1.0f;
            p.hudOpacity = 1.0f;
            p.maxRenderDistance = 400;
            p.focal = 300.0;
            p.smoothFade = true;
            p.fadeSpeed = 4.0f;
            p.minimapEnabled = true;
            p.minimapRadius = 80;
            p.minimapRangeMeters = 80;
            p.theme = Theme.STREAMER;
            return p;
        }
    }

    public static int getBaseColor() {
        Profile p = profile();
        if (p == null) return 0xFF66CCFF;
        switch (p.theme) {
            case DARK: return 0xFFAAAAFF;
            case LIGHT: return 0xFF333333;
            case STREAMER: return 0xFF66CCFF;
            case CUSTOM: default: return p.colorBase;
        }
    }

    public static int getOutlineColor(boolean trackedFlag) {
        Profile p = profile();
        if (p == null) return 0xFF66FFFF;
        if (trackedFlag) {
            return p.trackedColor;
        }
        switch (p.theme) {
            case DARK: return 0xFF88AAFF;
            case LIGHT: return 0xFFFFFFFF;
            case STREAMER: return 0xFF66FFFF;
            case CUSTOM: default: return p.colorOutline;
        }
    }

    public static synchronized void forceSaveNow() { save(); }
}