package com.github.aexmod.livedisplay;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Range;

import com.android.internal.util.lineageos.app.ContextConstants;
import com.github.aexmod.livedisplay.HSIC;

import java.io.UnsupportedEncodingException;
import java.lang.IllegalArgumentException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Manages access to LineageOS hardware extensions
 *
 *  <p>
 *  This manager requires the HARDWARE_ABSTRACTION_ACCESS permission.
 *  <p>
 *  To get the instance of this class, utilize LineageHardwareManager#getInstance(Context context)
 */
public final class LineageHardwareManager {
    private static final String TAG = "LineageHardwareManager";

    private static ILineageHardwareService sService;

    private Context mContext;

    /* The VisibleForTesting annotation is to ensure Proguard doesn't remove these
     * fields, as they might be used via reflection. When the @Keep annotation in
     * the support library is properly handled in the platform, we should change this.
     */

    /**
     * Adaptive backlight support (this refers to technologies like NVIDIA SmartDimmer,
     * QCOM CABL or Samsung CABC)
     */
    public static final int FEATURE_ADAPTIVE_BACKLIGHT = 0x1;

    /**
     * Color enhancement support
     */
    public static final int FEATURE_COLOR_ENHANCEMENT = 0x2;

    /**
     * Display RGB color calibration
     */
    public static final int FEATURE_DISPLAY_COLOR_CALIBRATION = 0x4;

    /**
     * Display gamma calibration
     */
    public static final int FEATURE_DISPLAY_GAMMA_CALIBRATION = 0x8;

    /**
     * High touch sensitivity for touch panels
     */
    public static final int FEATURE_HIGH_TOUCH_SENSITIVITY = 0x10;

    /**
     * Hardware navigation key disablement
     */
    public static final int FEATURE_KEY_DISABLE = 0x20;

    /**
     * Long term orbits (LTO)
     */
    public static final int FEATURE_LONG_TERM_ORBITS = 0x40;

    /**
     * Serial number other than ro.serialno
     */
    public static final int FEATURE_SERIAL_NUMBER = 0x80;

    /**
     * Increased display readability in bright light
     */
    public static final int FEATURE_SUNLIGHT_ENHANCEMENT = 0x100;

    /**
     * Variable vibrator intensity
     */
    public static final int FEATURE_VIBRATOR = 0x400;

    /**
     * Touchscreen hovering
     */
    public static final int FEATURE_TOUCH_HOVERING = 0x800;

    /**
     * Auto contrast
     */
    public static final int FEATURE_AUTO_CONTRAST = 0x1000;

    /**
     * Display modes
     */
    public static final int FEATURE_DISPLAY_MODES = 0x2000;

    /**
     * Reading mode
     */
    public static final int FEATURE_READING_ENHANCEMENT = 0x4000;

    /**
     * Color balance
     */
    public static final int FEATURE_COLOR_BALANCE = 0x20000;

    /**
     * HSIC picture adjustment
     */
    public static final int FEATURE_PICTURE_ADJUSTMENT = 0x40000;

    /**
     * Touchscreen gesture
     */
    public static final int FEATURE_TOUCHSCREEN_GESTURES = 0x80000;

    private static final List<Integer> BOOLEAN_FEATURES = Arrays.asList(
        FEATURE_ADAPTIVE_BACKLIGHT,
        FEATURE_COLOR_ENHANCEMENT,
        FEATURE_HIGH_TOUCH_SENSITIVITY,
        FEATURE_KEY_DISABLE,
        FEATURE_SUNLIGHT_ENHANCEMENT,
        FEATURE_TOUCH_HOVERING,
        FEATURE_AUTO_CONTRAST
    );

    private static LineageHardwareManager sLineageHardwareManagerInstance;

    /**
     * @hide to prevent subclassing from outside of the framework
     */
    private LineageHardwareManager(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }
        sService = getService();

        if (context.getPackageManager().hasSystemFeature(
                ContextConstants.Features.HARDWARE_ABSTRACTION) && !checkService()) {
            Log.wtf(TAG, "Unable to get LineageHardwareService. The service either" +
                    " crashed, was not started, or the interface has been called to early in" +
                    " SystemServer init");
        }
    }

    /**
     * Get or create an instance of the {@link lineageos.hardware.LineageHardwareManager}
     * @param context
     * @return {@link LineageHardwareManager}
     */
    public static LineageHardwareManager getInstance(Context context) {
        if (sLineageHardwareManagerInstance == null) {
            sLineageHardwareManagerInstance = new LineageHardwareManager(context);
        }
        return sLineageHardwareManagerInstance;
    }

    /** @hide */
    public static ILineageHardwareService getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(ContextConstants.LINEAGE_HARDWARE_SERVICE);
        if (b != null) {
            sService = ILineageHardwareService.Stub.asInterface(b);
            return sService;
        }
        return null;
    }

    /**
     * @return the supported features bitmask
     */
    public int getSupportedFeatures() {
        try {
            if (checkService()) {
                return sService.getSupportedFeatures();
            }
        } catch (RemoteException e) {
        }
        return 0;
    }

    /**
     * Determine if a Lineage Hardware feature is supported on this device
     *
     * @param feature The Lineage Hardware feature to query
     *
     * @return true if the feature is supported, false otherwise.
     */
    public boolean isSupported(int feature) {
        return feature == (getSupportedFeatures() & feature);
    }

    /**
     * String version for preference constraints
     *
     * @hide
     */
    public boolean isSupported(String feature) {
        if (!feature.startsWith("FEATURE_")) {
            return false;
        }
        try {
            Field f = getClass().getField(feature);
            if (f != null) {
                return isSupported((int) f.get(null));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.d(TAG, e.getMessage(), e);
        }

        return false;
    }
    /**
     * Determine if the given feature is enabled or disabled.
     *
     * Only used for features which have simple enable/disable controls.
     *
     * @param feature the Lineage Hardware feature to query
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean get(int feature) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }

        try {
            if (checkService()) {
                return sService.get(feature);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * Enable or disable the given feature
     *
     * Only used for features which have simple enable/disable controls.
     *
     * @param feature the Lineage Hardware feature to set
     * @param enable true to enable, false to disale
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean set(int feature, boolean enable) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }

        try {
            if (checkService()) {
                return sService.set(feature, enable);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    private int getArrayValue(int[] arr, int idx, int defaultValue) {
        if (arr == null || arr.length <= idx) {
            return defaultValue;
        }

        return arr[idx];
    }

    /**
     * {@hide}
     */
    public static final int VIBRATOR_INTENSITY_INDEX = 0;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_DEFAULT_INDEX = 1;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_MIN_INDEX = 2;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_MAX_INDEX = 3;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_WARNING_INDEX = 4;

    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_RED_INDEX = 0;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_GREEN_INDEX = 1;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_BLUE_INDEX = 2;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_DEFAULT_INDEX = 3;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_MIN_INDEX = 4;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_MAX_INDEX = 5;

    private int[] getDisplayColorCalibrationArray() {
        try {
            if (checkService()) {
                return sService.getDisplayColorCalibration();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return the current RGB calibration, where int[0] = R, int[1] = G, int[2] = B.
     */
    public int[] getDisplayColorCalibration() {
        int[] arr = getDisplayColorCalibrationArray();
        if (arr == null || arr.length < 3) {
            return null;
        }
        return Arrays.copyOf(arr, 3);
    }

    /**
     * @return the default value for all colors
     */
    public int getDisplayColorCalibrationDefault() {
        return getArrayValue(getDisplayColorCalibrationArray(), COLOR_CALIBRATION_DEFAULT_INDEX, 0);
    }

    /**
     * @return The minimum value for all colors
     */
    public int getDisplayColorCalibrationMin() {
        return getArrayValue(getDisplayColorCalibrationArray(), COLOR_CALIBRATION_MIN_INDEX, 0);
    }

    /**
     * @return The minimum value for all colors
     */
    public int getDisplayColorCalibrationMax() {
        return getArrayValue(getDisplayColorCalibrationArray(), COLOR_CALIBRATION_MAX_INDEX, 0);
    }

    /**
     * Set the display color calibration to the given rgb triplet
     *
     * @param rgb RGB color calibration.  Each value must be between
     * {@link #getDisplayColorCalibrationMin()} and {@link #getDisplayColorCalibrationMax()},
     * inclusive.
     *
     * @return true on success, false otherwise.
     */
    public boolean setDisplayColorCalibration(int[] rgb) {
        try {
            if (checkService()) {
                return sService.setDisplayColorCalibration(rgb);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * {@hide}
     */
    public static final int GAMMA_CALIBRATION_RED_INDEX = 0;
    /**
     * {@hide}
     */
    public static final int GAMMA_CALIBRATION_GREEN_INDEX = 1;
    /**
     * {@hide}
     */
    public static final int GAMMA_CALIBRATION_BLUE_INDEX = 2;
    /**
     * {@hide}
     */
    public static final int GAMMA_CALIBRATION_MIN_INDEX = 3;
    /**
     * {@hide}
     */
    public static final int GAMMA_CALIBRATION_MAX_INDEX = 4;

    private int[] getDisplayGammaCalibrationArray(int idx) {
        try {
            if (checkService()) {
                return sService.getDisplayGammaCalibration(idx);
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return the number of RGB controls the device supports
     *
     * @deprecated
     */
    @Deprecated
    public int getNumGammaControls() {
        try {
            if (checkService()) {
                return sService.getNumGammaControls();
            }
        } catch (RemoteException e) {
        }
        return 0;
    }

    /**
     * @param idx the control to query
     *
     * @return the current RGB gamma calibration for the given control
     *
     * @deprecated
     */
    @Deprecated
    public int[] getDisplayGammaCalibration(int idx) {
        int[] arr = getDisplayGammaCalibrationArray(idx);
        if (arr == null || arr.length < 3) {
            return null;
        }
        return Arrays.copyOf(arr, 3);
    }

    /**
     * @return the minimum value for all colors
     *
     * @deprecated
     */
    @Deprecated
    public int getDisplayGammaCalibrationMin() {
        return getArrayValue(getDisplayGammaCalibrationArray(0), GAMMA_CALIBRATION_MIN_INDEX, 0);
    }

    /**
     * @return the maximum value for all colors
     *
     * @deprecated
     */
    @Deprecated
    public int getDisplayGammaCalibrationMax() {
        return getArrayValue(getDisplayGammaCalibrationArray(0), GAMMA_CALIBRATION_MAX_INDEX, 0);
    }

    /**
     * Set the display gamma calibration for a specific control
     *
     * @param idx the control to set
     * @param rgb RGB color calibration.  Each value must be between
     * {@link #getDisplayGammaCalibrationMin()} and {@link #getDisplayGammaCalibrationMax()},
     * inclusive.
     *
     * @return true on success, false otherwise.
     *
     * @deprecated
     */
    @Deprecated
    public boolean setDisplayGammaCalibration(int idx, int[] rgb) {
        try {
            if (checkService()) {
                return sService.setDisplayGammaCalibration(idx, rgb);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return true if adaptive backlight should be enabled when sunlight enhancement
     * is enabled.
     */
    public boolean requireAdaptiveBacklightForSunlightEnhancement() {
        try {
            if (checkService()) {
                return sService.requireAdaptiveBacklightForSunlightEnhancement();
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return true if this implementation does it's own lux metering
     */
    public boolean isSunlightEnhancementSelfManaged() {
        try {
            if (checkService()) {
                return sService.isSunlightEnhancementSelfManaged();
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return a list of available display modes on the devices
     */
    public DisplayMode[] getDisplayModes() {
        try {
            if (checkService()) {
                return sService.getDisplayModes();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return the currently active display mode
     */
    public DisplayMode getCurrentDisplayMode() {
        try {
            if (checkService()) {
                return sService.getCurrentDisplayMode();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return the default display mode to be set on boot
     */
    public DisplayMode getDefaultDisplayMode() {
        try {
            if (checkService()) {
                return sService.getDefaultDisplayMode();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return true if setting the mode was successful
     */
    public boolean setDisplayMode(DisplayMode mode, boolean makeDefault) {
        try {
            if (checkService()) {
                return sService.setDisplayMode(mode, makeDefault);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return the available range for color temperature adjustments
     */
    public Range<Integer> getColorBalanceRange() {
        int min = 0;
        int max = 0;
        try {
            if (checkService()) {
                min = sService.getColorBalanceMin();
                max = sService.getColorBalanceMax();
            }
        } catch (RemoteException e) {
        }
        return new Range<Integer>(min, max);
    }

    /**
     * @return the current color balance value
     */
    public int getColorBalance() {
        try {
            if (checkService()) {
                return sService.getColorBalance();
            }
        } catch (RemoteException e) {
        }
        return 0;
    }

    /**
     * Sets the desired color balance. Must fall within the range obtained from
     * getColorBalanceRange()
     *
     * @param value
     * @return true if success
     */
    public boolean setColorBalance(int value) {
        try {
            if (checkService()) {
                return sService.setColorBalance(value);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * Gets the current picture adjustment values
     *
     * @return HSIC object with current settings
     */
    public HSIC getPictureAdjustment() {
        try {
            if (checkService()) {
                return sService.getPictureAdjustment();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * Gets the default picture adjustment for the current mode
     *
     * @return HSIC object with default settings
     */
    public HSIC getDefaultPictureAdjustment() {
        try {
            if (checkService()) {
                return sService.getDefaultPictureAdjustment();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * Sets the desired hue/saturation/intensity/contrast
     *
     * @param hsic
     * @return true if success
     */
    public boolean setPictureAdjustment(final HSIC hsic) {
        try {
            if (checkService()) {
                return sService.setPictureAdjustment(hsic);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * Get a list of ranges valid for picture adjustment.
     *
     * @return range list
     */
    public List<Range<Float>> getPictureAdjustmentRanges() {
        try {
            if (checkService()) {
                float[] ranges = sService.getPictureAdjustmentRanges();
                if (ranges.length > 7) {
                    return Arrays.asList(new Range<Float>(ranges[0], ranges[1]),
                            new Range<Float>(ranges[2], ranges[3]),
                            new Range<Float>(ranges[4], ranges[5]),
                            new Range<Float>(ranges[6], ranges[7]),
                            (ranges.length > 9 ?
                                    new Range<Float>(ranges[8], ranges[9]) :
                                    new Range<Float>(0.0f, 0.0f)));
                }
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return true if service is valid
     */
    private boolean checkService() {
        if (sService == null) {
            Log.w(TAG, "not connected to LineageHardwareManagerService");
            return false;
        }
        return true;
    }

    /**
     * Enables or disables reading mode
     *
     * @return true if success
     */
    public boolean setGrayscale(boolean state) {
        try {
            if (checkService()) {
                return sService.setGrayscale(state);
            }
        } catch (RemoteException e) {
        }
        return false;
    }
}