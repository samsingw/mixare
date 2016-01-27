package org.mixare;

import android.location.Location;

public class Config {
    /** TAG for logging */
    public static final String TAG = "Mixare";
    /** string to name & access the preference file in the internal storage */
    public static final String PREFS_NAME = "MyPrefsFileForMenuItems";
    public static final int DEFAULT_RANGE = 65;
    public final static double DEFAULT_FIX_LAT =51.46184;
    public final static double DEFAULT_FIX_LON =7.01655;
    public final static int DEFAULT_FIX_HEIGHT =300;
    public final static String DEFAULT_FIX_NAME ="defaultFix";
    public static boolean drawTextBlock = true;

    //currently only for test purposes
    public static boolean useHUD=true;

    public static Location getDefaultFix(){
        Location defaultFix = new Location(DEFAULT_FIX_NAME);

        defaultFix.setLatitude(DEFAULT_FIX_LAT);
        defaultFix.setLongitude(DEFAULT_FIX_LON);
        defaultFix.setAltitude(DEFAULT_FIX_HEIGHT);
        return defaultFix;
    }
}