package com.zj.playerLib.util;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Parcel;
import android.security.NetworkSecurityPolicy;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.Display;
import android.view.Display.Mode;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.zj.playerLib.C;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.SeekParameters;
import com.zj.playerLib.upstream.DataSource;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Formatter;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;


public final class Util {
    public static final int SDK_INT;
    public static final String DEVICE;
    public static final String MANUFACTURER;
    public static final String MODEL;
    public static final String DEVICE_DEBUG_INFO;
    public static final byte[] EMPTY_BYTE_ARRAY;
    private static final String TAG = "Util";
    private static final Pattern XS_DATE_TIME_PATTERN;
    private static final Pattern XS_DURATION_PATTERN;
    private static final Pattern ESCAPED_CHARACTER_PATTERN;
    private static final int[] CRC32_BYTES_MSBF;

    private Util() {
    }

    public static byte[] toByteArray(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        return outputStream.toByteArray();
    }

    public static ComponentName startForegroundService(Context context, Intent intent) {
        return SDK_INT >= 26 ? context.startForegroundService(intent) : context.startService(intent);
    }

    @TargetApi(24)
    public static boolean checkCleartextTrafficPermitted(Uri... uris) {
        if (SDK_INT >= 24) {
            for (Uri uri : uris) {
                if ("http".equals(uri.getScheme()) && !NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(uri.getHost())) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isLocalFileUri(Uri uri) {
        String scheme = uri.getScheme();
        return TextUtils.isEmpty(scheme) || "file".equals(scheme);
    }

    public static boolean areEqual(@Nullable Object o1, @Nullable Object o2) {
        if (o1 == null) return o2 == null;
        return o1.equals(o2);
    }

    public static boolean contains(Object[] items, Object item) {
        for (Object arrayItem : items) {
            if (areEqual(arrayItem, item)) {
                return true;
            }
        }
        return false;
    }

    public static <T> void removeRange(List<T> list, int fromIndex, int toIndex) {
        if (fromIndex >= 0 && toIndex <= list.size() && fromIndex <= toIndex) {
            if (fromIndex != toIndex) {
                list.subList(fromIndex, toIndex).clear();
            }

        } else {
            throw new IllegalArgumentException();
        }
    }

    public static <T> T castNonNull(@Nullable T value) {
        return value;
    }

    public static <T> T[] castNonNullTypeArray(T[] value) {
        return value;
    }

    public static <T> T[] nullSafeArrayCopy(T[] input, int length) {
        Assertions.checkArgument(length <= input.length);
        return Arrays.copyOf(input, length);
    }

    public static Handler createHandler(Callback callback) {
        return createHandler(getLooper(), callback);
    }

    public static Handler createHandler(Looper looper, Callback callback) {
        return new Handler(looper, callback);
    }

    public static Looper getLooper() {
        Looper myLooper = Looper.myLooper();
        return myLooper != null ? myLooper : Looper.getMainLooper();
    }

    public static ExecutorService newSingleThreadExecutor(String threadName) {
        return Executors.newSingleThreadExecutor((runnable) -> new Thread(runnable, threadName));
    }

    public static void closeQuietly(DataSource dataSource) {
        try {
            if (dataSource != null) {
                dataSource.close();
            }
        } catch (IOException var2) {
            var2.printStackTrace();
        }

    }

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException var2) {
            var2.printStackTrace();
        }

    }

    public static boolean readBoolean(Parcel parcel) {
        return parcel.readInt() != 0;
    }

    public static void writeBoolean(Parcel parcel, boolean value) {
        parcel.writeInt(value ? 1 : 0);
    }

    @Nullable
    public static String normalizeLanguageCode(@Nullable String language) {
        try {
            return language == null ? null : (new Locale(language)).getISO3Language();
        } catch (MissingResourceException var2) {
            return toLowerInvariant(language);
        }
    }

    public static String fromUtf8Bytes(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String fromUtf8Bytes(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.UTF_8);
    }

    public static byte[] getUtf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static String[] split(String value, String regex) {
        return value.split(regex, -1);
    }

    public static String[] splitAtFirst(String value, String regex) {
        return value.split(regex, 2);
    }

    public static boolean isLinebreak(int c) {
        return c == 10 || c == 13;
    }

    public static String toLowerInvariant(String text) {
        return text == null ? text : text.toLowerCase(Locale.US);
    }

    public static String toUpperInvariant(String text) {
        return text == null ? text : text.toUpperCase(Locale.US);
    }

    public static String formatInvariant(String format, Object... args) {
        return String.format(Locale.US, format, args);
    }

    public static int ceilDivide(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }

    public static long ceilDivide(long numerator, long denominator) {
        return (numerator + denominator - 1L) / denominator;
    }

    public static int constrainValue(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public static long constrainValue(long value, long min, long max) {
        return Math.max(min, Math.min(value, max));
    }

    public static float constrainValue(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    public static long addWithOverflowDefault(long x, long y, long overflowResult) {
        long result = x + y;
        return ((x ^ result) & (y ^ result)) < 0L ? overflowResult : result;
    }

    public static long subtractWithOverflowDefault(long x, long y, long overflowResult) {
        long result = x - y;
        return ((x ^ y) & (x ^ result)) < 0L ? overflowResult : result;
    }

    public static int binarySearchFloor(int[] array, int value, boolean inclusive, boolean stayInBounds) {
        int index = Arrays.binarySearch(array, value);
        if (index < 0) {
            index = -(index + 2);
        } else {
            while (true) {
                --index;
                if (index < 0 || array[index] != value) {
                    if (inclusive) {
                        ++index;
                    }
                    break;
                }
            }
        }

        return stayInBounds ? Math.max(0, index) : index;
    }

    public static int binarySearchFloor(long[] array, long value, boolean inclusive, boolean stayInBounds) {
        int index = Arrays.binarySearch(array, value);
        if (index < 0) {
            index = -(index + 2);
        } else {
            while (true) {
                --index;
                if (index < 0 || array[index] != value) {
                    if (inclusive) {
                        ++index;
                    }
                    break;
                }
            }
        }

        return stayInBounds ? Math.max(0, index) : index;
    }

    public static <T extends Comparable<? super T>> int binarySearchFloor(List<? extends Comparable<? super T>> list, T value, boolean inclusive, boolean stayInBounds) {
        int index = Collections.binarySearch(list, value);
        if (index < 0) {
            index = -(index + 2);
        } else {
            while (true) {
                --index;
                if (index < 0 || list.get(index).compareTo(value) != 0) {
                    if (inclusive) {
                        ++index;
                    }
                    break;
                }
            }
        }

        return stayInBounds ? Math.max(0, index) : index;
    }

    public static int binarySearchCeil(long[] array, long value, boolean inclusive, boolean stayInBounds) {
        int index = Arrays.binarySearch(array, value);
        if (index < 0) {
            index = ~index;
        } else {
            while (true) {
                ++index;
                if (index >= array.length || array[index] != value) {
                    if (inclusive) {
                        --index;
                    }
                    break;
                }
            }
        }

        return stayInBounds ? Math.min(array.length - 1, index) : index;
    }

    public static <T extends Comparable<? super T>> int binarySearchCeil(List<? extends Comparable<? super T>> list, T value, boolean inclusive, boolean stayInBounds) {
        int index = Collections.binarySearch(list, value);
        if (index < 0) {
            index = ~index;
        } else {
            int listSize = list.size();

            do {
                ++index;
            } while (index < listSize && list.get(index).compareTo(value) == 0);
            if (inclusive) {
                --index;
            }
        }

        return stayInBounds ? Math.min(list.size() - 1, index) : index;
    }

    public static int compareLong(long left, long right) {
        return Long.compare(left, right);
    }

    public static long parseXsDuration(String value) {
        Matcher matcher = XS_DURATION_PATTERN.matcher(value);
        if (matcher.matches()) {
            boolean negated = !TextUtils.isEmpty(matcher.group(1));
            String years = matcher.group(3);
            double durationSeconds = years != null ? Double.parseDouble(years) * 3.1556908E7D : 0.0D;
            String months = matcher.group(5);
            durationSeconds += months != null ? Double.parseDouble(months) * 2629739.0D : 0.0D;
            String days = matcher.group(7);
            durationSeconds += days != null ? Double.parseDouble(days) * 86400.0D : 0.0D;
            String hours = matcher.group(10);
            durationSeconds += hours != null ? Double.parseDouble(hours) * 3600.0D : 0.0D;
            String minutes = matcher.group(12);
            durationSeconds += minutes != null ? Double.parseDouble(minutes) * 60.0D : 0.0D;
            String seconds = matcher.group(14);
            durationSeconds += seconds != null ? Double.parseDouble(seconds) : 0.0D;
            long durationMillis = (long) (durationSeconds * 1000.0D);
            return negated ? -durationMillis : durationMillis;
        } else {
            return (long) (Double.parseDouble(value) * 3600.0D * 1000.0D);
        }
    }

    public static long parseXsDateTime(String value) throws ParserException {
        Matcher matcher = XS_DATE_TIME_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new ParserException("Invalid date/time format: " + value);
        } else {
            int timezoneShift;
            String s = matcher.group(9);
            if (s == null) {
                timezoneShift = 0;
            } else if (s.equalsIgnoreCase("Z")) {
                timezoneShift = 0;
            } else {
                String s12 = matcher.group(12);
                String s13 = matcher.group(13);
                if (s12 == null || s13 == null) timezoneShift = 0;
                else {
                    timezoneShift = Integer.parseInt(s12) * 60 + Integer.parseInt(s13);
                    if ("-".equals(matcher.group(11))) {
                        timezoneShift *= -1;
                    }
                }
            }

            Calendar dateTime = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
            dateTime.clear();
            String year = matcher.group(1) + "";
            String month = matcher.group(2) + "";
            String date = matcher.group(3) + "";
            String hourOfDay = matcher.group(4) + "";
            String minute = matcher.group(5) + "";
            String second = matcher.group(6) + "";
            dateTime.set(Integer.parseInt(year), Integer.parseInt(month) - 1, Integer.parseInt(date), Integer.parseInt(hourOfDay), Integer.parseInt(minute), Integer.parseInt(second));
            if (!TextUtils.isEmpty(matcher.group(8))) {
                BigDecimal bd = new BigDecimal("0." + matcher.group(8));
                dateTime.set(14, bd.movePointRight(3).intValue());
            }

            long time = dateTime.getTimeInMillis();
            if (timezoneShift != 0) {
                time -= timezoneShift * '\uea60';
            }

            return time;
        }
    }

    public static long scaleLargeTimestamp(long timestamp, long multiplier, long divisor) {
        double multiplicationFactor;
        if (divisor >= multiplier && divisor % multiplier == 0L) {
            multiplicationFactor = divisor * 1.0D / multiplier;
            return (long) (timestamp / multiplicationFactor);
        } else if (divisor < multiplier && multiplier % divisor == 0L) {
            multiplicationFactor = multiplier * 1.0D / divisor;
            return (long) (timestamp * multiplicationFactor);
        } else {
            multiplicationFactor = (double) multiplier / (double) divisor;
            return (long) ((double) timestamp * multiplicationFactor);
        }
    }

    public static long[] scaleLargeTimestamps(List<Long> timestamps, long multiplier, long divisor) {
        long[] scaledTimestamps = new long[timestamps.size()];
        int i;
        if (divisor >= multiplier && divisor % multiplier == 0L) {
            long multiplicationFactor = divisor / multiplier;
            for (i = 0; i < scaledTimestamps.length; ++i) {
                scaledTimestamps[i] = timestamps.get(i) / multiplicationFactor;
            }
        } else if (divisor < multiplier && multiplier % divisor == 0L) {
            long multiplicationFactor = multiplier / divisor;
            for (i = 0; i < scaledTimestamps.length; ++i) {
                scaledTimestamps[i] = timestamps.get(i) * multiplicationFactor;
            }
        } else {
            double multiplicationFactor = (double) multiplier / (double) divisor;
            for (i = 0; i < scaledTimestamps.length; ++i) {
                scaledTimestamps[i] = (long) ((double) timestamps.get(i) * multiplicationFactor);
            }
        }

        return scaledTimestamps;
    }

    public static void scaleLargeTimestampsInPlace(long[] timestamps, long multiplier, long divisor) {
        int i;
        if (divisor >= multiplier && divisor % multiplier == 0L) {
            long multiplicationFactor = divisor / multiplier;

            for (i = 0; i < timestamps.length; ++i) {
                timestamps[i] /= multiplicationFactor;
            }
        } else if (divisor < multiplier && multiplier % divisor == 0L) {
            long multiplicationFactor = multiplier / divisor;

            for (i = 0; i < timestamps.length; ++i) {
                timestamps[i] *= multiplicationFactor;
            }
        } else {
            double multiplicationFactor = (double) multiplier / (double) divisor;
            for (i = 0; i < timestamps.length; ++i) {
                timestamps[i] = (long) ((double) timestamps[i] * multiplicationFactor);
            }
        }

    }

    public static long getMediaDurationForPlayoutDuration(long playoutDuration, float speed) {
        return speed == 1.0F ? playoutDuration : Math.round((double) playoutDuration * (double) speed);
    }

    public static long getPlayoutDurationForMediaDuration(long mediaDuration, float speed) {
        return speed == 1.0F ? mediaDuration : Math.round((double) mediaDuration / (double) speed);
    }

    public static long resolveSeekPositionUs(long positionUs, SeekParameters seekParameters, long firstSyncUs, long secondSyncUs) {
        if (SeekParameters.EXACT.equals(seekParameters)) {
            return positionUs;
        } else {
            long minPositionUs = subtractWithOverflowDefault(positionUs, seekParameters.toleranceBeforeUs, -9223372036854775808L);
            long maxPositionUs = addWithOverflowDefault(positionUs, seekParameters.toleranceAfterUs, Long.MAX_VALUE);
            boolean firstSyncPositionValid = minPositionUs <= firstSyncUs && firstSyncUs <= maxPositionUs;
            boolean secondSyncPositionValid = minPositionUs <= secondSyncUs && secondSyncUs <= maxPositionUs;
            if (firstSyncPositionValid && secondSyncPositionValid) {
                return Math.abs(firstSyncUs - positionUs) <= Math.abs(secondSyncUs - positionUs) ? firstSyncUs : secondSyncUs;
            } else if (firstSyncPositionValid) {
                return firstSyncUs;
            } else {
                return secondSyncPositionValid ? secondSyncUs : minPositionUs;
            }
        }
    }

    public static int[] toArray(List<Integer> list) {
        if (list == null) {
            return null;
        } else {
            int length = list.size();
            int[] intArray = new int[length];

            for (int i = 0; i < length; ++i) {
                intArray[i] = list.get(i);
            }
            return intArray;
        }
    }

    public static int getIntegerCodeForString(String string) {
        int length = string.length();
        Assertions.checkArgument(length <= 4);
        int result = 0;

        for (int i = 0; i < length; ++i) {
            result <<= 8;
            result |= string.charAt(i);
        }

        return result;
    }

    public static byte[] getBytesFromHexString(String hexString) {
        byte[] data = new byte[hexString.length() / 2];

        for (int i = 0; i < data.length; ++i) {
            int stringOffset = i * 2;
            data[i] = (byte) ((Character.digit(hexString.charAt(stringOffset), 16) << 4) + Character.digit(hexString.charAt(stringOffset + 1), 16));
        }

        return data;
    }

    public static String getCommaDelimitedSimpleClassNames(Object[] objects) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < objects.length; ++i) {
            stringBuilder.append(objects[i].getClass().getSimpleName());
            if (i < objects.length - 1) {
                stringBuilder.append(", ");
            }
        }

        return stringBuilder.toString();
    }

    public static String getUserAgent(Context context, String applicationName) {
        String versionName;
        try {
            String packageName = context.getPackageName();
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            versionName = info.versionName;
        } catch (NameNotFoundException var5) {
            versionName = "?";
        }

        return applicationName + "/" + versionName + " (Linux;Android " + VERSION.RELEASE + ") " + "ExoPlayerLib/2.9.3";
    }

    @Nullable
    public static String getCodecsOfType(String codecs, int trackType) {
        String[] codecArray = splitCodecs(codecs);
        if (codecArray.length == 0) {
            return null;
        } else {
            StringBuilder builder = new StringBuilder();
            String[] var4 = codecArray;
            int var5 = codecArray.length;

            for (int var6 = 0; var6 < var5; ++var6) {
                String codec = var4[var6];
                if (trackType == MimeTypes.getTrackTypeOfCodec(codec)) {
                    if (builder.length() > 0) {
                        builder.append(",");
                    }

                    builder.append(codec);
                }
            }

            return builder.length() > 0 ? builder.toString() : null;
        }
    }

    public static String[] splitCodecs(String codecs) {
        return TextUtils.isEmpty(codecs) ? new String[0] : split(codecs.trim(), "(\\s*,\\s*)");
    }

    public static int getPcmEncoding(int bitDepth) {
        switch (bitDepth) {
            case 8:
                return 3;
            case 16:
                return 2;
            case 24:
                return -2147483648;
            case 32:
                return 1073741824;
            default:
                return 0;
        }
    }

    public static boolean isEncodingLinearPcm(int encoding) {
        return encoding == 3 || encoding == 2 || encoding == -2147483648 || encoding == 1073741824 || encoding == 4;
    }

    public static boolean isEncodingHighResolutionIntegerPcm(int encoding) {
        return encoding == -2147483648 || encoding == 1073741824;
    }

    public static int getAudioTrackChannelConfig(int channelCount) {
        switch (channelCount) {
            case 1:
                return 4;
            case 2:
                return 12;
            case 3:
                return 28;
            case 4:
                return 204;
            case 5:
                return 220;
            case 6:
                return 252;
            case 7:
                return 1276;
            case 8:
                if (SDK_INT >= 23) {
                    return 6396;
                } else {
                    if (SDK_INT >= 21) {
                        return 6396;
                    }

                    return 0;
                }
            default:
                return 0;
        }
    }

    public static int getPcmFrameSize(int pcmEncoding, int channelCount) {
        switch (pcmEncoding) {
            case -2147483648:
                return channelCount * 3;
            case -1:
            case 0:
            case 268435456:
            case 536870912:
            default:
                throw new IllegalArgumentException();
            case 2:
                return channelCount * 2;
            case 3:
                return channelCount;
            case 4:
            case 1073741824:
                return channelCount * 4;
        }
    }

    public static int getAudioUsageForStreamType(int streamType) {
        switch (streamType) {
            case -2147483648:
            case 3:
            default:
                return 1;
            case 0:
                return 2;
            case 1:
                return 13;
            case 2:
                return 6;
            case 4:
                return 4;
            case 5:
                return 5;
            case 8:
                return 3;
        }
    }

    public static int getAudioContentTypeForStreamType(int streamType) {
        switch (streamType) {
            case -2147483648:
            case 3:
            default:
                return 2;
            case 0:
                return 1;
            case 1:
            case 2:
            case 4:
            case 5:
            case 8:
                return 4;
        }
    }

    public static int getStreamTypeForAudioUsage(int usage) {
        switch (usage) {
            case 0:
            case 11:
            case 15:
            case 16:
            default:
                return 3;
            case 1:
            case 12:
            case 14:
                return 3;
            case 2:
                return 0;
            case 3:
                return 8;
            case 4:
                return 4;
            case 5:
            case 7:
            case 8:
            case 9:
            case 10:
                return 5;
            case 6:
                return 2;
            case 13:
                return 1;
        }
    }

    @Nullable
    public static UUID getDrmUuid(String drmScheme) {
        String var1 = toLowerInvariant(drmScheme);
        byte var2 = -1;
        switch (var1.hashCode()) {
            case -1860423953:
                if (var1.equals("playready")) {
                    var2 = 1;
                }
                break;
            case -1400551171:
                if (var1.equals("widevine")) {
                    var2 = 0;
                }
                break;
            case 790309106:
                if (var1.equals("clearkey")) {
                    var2 = 2;
                }
        }

        switch (var2) {
            case 0:
                return C.WIDEVINE_UUID;
            case 1:
                return C.PLAYREADY_UUID;
            case 2:
                return C.CLEARKEY_UUID;
            default:
                try {
                    return UUID.fromString(drmScheme);
                } catch (RuntimeException var4) {
                    return null;
                }
        }
    }

    public static int inferContentType(Uri uri, String overrideExtension) {
        return TextUtils.isEmpty(overrideExtension) ? inferContentType(uri) : inferContentType("." + overrideExtension);
    }

    public static int inferContentType(Uri uri) {
        String path = uri.getPath();
        return path == null ? 3 : inferContentType(path);
    }

    public static int inferContentType(String fileName) {
        fileName = toLowerInvariant(fileName);
        if (fileName.endsWith(".mpd")) {
            return 0;
        } else if (fileName.endsWith(".m3u8")) {
            return 2;
        } else {
            return fileName.matches(".*\\.ism(l)?(/manifest(\\(.+\\))?)?") ? 1 : 3;
        }
    }

    public static String getStringForTime(StringBuilder builder, Formatter formatter, long timeMs) {
        if (timeMs == -Long.MAX_VALUE) {
            timeMs = 0L;
        }

        long totalSeconds = (timeMs + 500L) / 1000L;
        long seconds = totalSeconds % 60L;
        long minutes = totalSeconds / 60L % 60L;
        long hours = totalSeconds / 3600L;
        builder.setLength(0);
        return hours > 0L ? formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString() : formatter.format("%02d:%02d", minutes, seconds).toString();
    }

    public static int getDefaultBufferSize(int trackType) {
        switch (trackType) {
            case 0:
                return 16777216;
            case 1:
                return 3538944;
            case 2:
                return 13107200;
            case 3:
                return 131072;
            case 4:
                return 131072;
            case 5:
                return 131072;
            case 6:
                return 0;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static String escapeFileName(String fileName) {
        int length = fileName.length();
        int charactersToEscapeCount = 0;

        int i;
        for (i = 0; i < length; ++i) {
            if (shouldEscapeCharacter(fileName.charAt(i))) {
                ++charactersToEscapeCount;
            }
        }

        if (charactersToEscapeCount == 0) {
            return fileName;
        } else {
            i = 0;
            StringBuilder builder = new StringBuilder(length + charactersToEscapeCount * 2);

            while (charactersToEscapeCount > 0) {
                char c = fileName.charAt(i++);
                if (shouldEscapeCharacter(c)) {
                    builder.append('%').append(Integer.toHexString(c));
                    --charactersToEscapeCount;
                } else {
                    builder.append(c);
                }
            }

            if (i < length) {
                builder.append(fileName, i, length);
            }

            return builder.toString();
        }
    }

    private static boolean shouldEscapeCharacter(char c) {
        switch (c) {
            case '"':
            case '%':
            case '*':
            case '/':
            case ':':
            case '<':
            case '>':
            case '?':
            case '\\':
            case '|':
                return true;
            default:
                return false;
        }
    }

    @Nullable
    public static String unescapeFileName(String fileName) {
        int length = fileName.length();
        int percentCharacterCount = 0;

        int expectedLength;
        for (expectedLength = 0; expectedLength < length; ++expectedLength) {
            if (fileName.charAt(expectedLength) == '%') {
                ++percentCharacterCount;
            }
        }

        if (percentCharacterCount == 0) {
            return fileName;
        } else {
            expectedLength = length - percentCharacterCount * 2;
            StringBuilder builder = new StringBuilder(expectedLength);
            Matcher matcher = ESCAPED_CHARACTER_PATTERN.matcher(fileName);

            int startOfNotEscaped;
            for (startOfNotEscaped = 0; percentCharacterCount > 0 && matcher.find(); --percentCharacterCount) {
                String s = matcher.group(1) + "";
                char unescapedCharacter = (char) Integer.parseInt(s, 16);
                builder.append(fileName, startOfNotEscaped, matcher.start()).append(unescapedCharacter);
                startOfNotEscaped = matcher.end();
            }

            if (startOfNotEscaped < length) {
                builder.append(fileName, startOfNotEscaped, length);
            }

            return builder.length() != expectedLength ? null : builder.toString();
        }
    }

    public static File createTempFile(Context context, String prefix) throws IOException {
        return File.createTempFile(prefix, null, context.getCacheDir());
    }

    public static int crc(byte[] bytes, int start, int end, int initialValue) {
        for (int i = start; i < end; ++i) {
            initialValue = initialValue << 8 ^ CRC32_BYTES_MSBF[(initialValue >>> 24 ^ bytes[i] & 255) & 255];
        }

        return initialValue;
    }

    public static int getNetworkType(@Nullable Context context) {
        if (context == null) {
            return 0;
        } else {
            NetworkInfo networkInfo;
            try {
                ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivityManager == null) {
                    return 0;
                }

                networkInfo = connectivityManager.getActiveNetworkInfo();
            } catch (SecurityException var3) {
                return 0;
            }

            if (networkInfo != null && networkInfo.isConnected()) {
                switch (networkInfo.getType()) {
                    case 0:
                    case 4:
                    case 5:
                        return getMobileNetworkType(networkInfo);
                    case 1:
                        return 2;
                    case 2:
                    case 3:
                    case 7:
                    case 8:
                    default:
                        return 8;
                    case 6:
                        return 5;
                    case 9:
                        return 7;
                }
            } else {
                return 1;
            }
        }
    }

    public static String getCountryCode(@Nullable Context context) {
        if (context != null) {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                String countryCode = telephonyManager.getNetworkCountryIso();
                if (!TextUtils.isEmpty(countryCode)) {
                    return toUpperInvariant(countryCode);
                }
            }
        }

        return toUpperInvariant(Locale.getDefault().getCountry());
    }

    public static boolean inflate(ParsableByteArray input, ParsableByteArray output, @Nullable Inflater inflater) {
        if (input.bytesLeft() <= 0) {
            return false;
        } else {
            byte[] outputData = output.data;
            if (outputData.length < input.bytesLeft()) {
                outputData = new byte[2 * input.bytesLeft()];
            }

            if (inflater == null) {
                inflater = new Inflater();
            }

            inflater.setInput(input.data, input.getPosition(), input.bytesLeft());

            try {
                boolean var5;
                try {
                    int outputSize = 0;

                    while (true) {
                        outputSize += inflater.inflate(outputData, outputSize, outputData.length - outputSize);
                        if (inflater.finished()) {
                            output.reset(outputData, outputSize);
                            var5 = true;
                            return var5;
                        }

                        if (inflater.needsDictionary() || inflater.needsInput()) {
                            var5 = false;
                            return var5;
                        }

                        if (outputSize == outputData.length) {
                            outputData = Arrays.copyOf(outputData, outputData.length * 2);
                        }
                    }
                } catch (DataFormatException var9) {
                    var5 = false;
                    return var5;
                }
            } finally {
                inflater.reset();
            }
        }
    }

    public static Point getPhysicalDisplaySize(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        return getPhysicalDisplaySize(context, windowManager.getDefaultDisplay());
    }

    public static Point getPhysicalDisplaySize(Context context, Display display) {
        if (SDK_INT < 25 && display.getDisplayId() == 0) {
            if ("Sony".equals(MANUFACTURER) && MODEL.startsWith("BRAVIA") && context.getPackageManager().hasSystemFeature("com.sony.dtv.hardware.panel.qfhd")) {
                return new Point(3840, 2160);
            }

            if ("NVIDIA".equals(MANUFACTURER) && MODEL.contains("SHIELD") || "philips".equals(toLowerInvariant(MANUFACTURER)) && (MODEL.startsWith("QM1") || MODEL.equals("QV151E") || MODEL.equals("TPM171E"))) {
                String sysDisplaySize = null;

                try {
                    Class<?> systemProperties = Class.forName("android.os.SystemProperties");
                    Method getMethod = systemProperties.getMethod("get", String.class);
                    sysDisplaySize = (String) getMethod.invoke(systemProperties, "sys.display-size");
                } catch (Exception var7) {
                    Log.e("Util", "Failed to read sys.display-size", var7);
                }

                if (!TextUtils.isEmpty(sysDisplaySize)) {
                    try {
                        String[] sysDisplaySizeParts = split(sysDisplaySize.trim(), "x");
                        if (sysDisplaySizeParts.length == 2) {
                            int width = Integer.parseInt(sysDisplaySizeParts[0]);
                            int height = Integer.parseInt(sysDisplaySizeParts[1]);
                            if (width > 0 && height > 0) {
                                return new Point(width, height);
                            }
                        }
                    } catch (NumberFormatException var6) {
                    }

                    Log.e("Util", "Invalid sys.display-size: " + sysDisplaySize);
                }
            }
        }

        Point displaySize = new Point();
        if (SDK_INT >= 23) {
            getDisplaySizeV23(display, displaySize);
        } else if (SDK_INT >= 17) {
            getDisplaySizeV17(display, displaySize);
        } else if (SDK_INT >= 16) {
            getDisplaySizeV16(display, displaySize);
        } else {
            getDisplaySizeV9(display, displaySize);
        }

        return displaySize;
    }

    @TargetApi(23)
    private static void getDisplaySizeV23(Display display, Point outSize) {
        Mode mode = display.getMode();
        outSize.x = mode.getPhysicalWidth();
        outSize.y = mode.getPhysicalHeight();
    }

    @TargetApi(17)
    private static void getDisplaySizeV17(Display display, Point outSize) {
        display.getRealSize(outSize);
    }

    @TargetApi(16)
    private static void getDisplaySizeV16(Display display, Point outSize) {
        display.getSize(outSize);
    }

    private static void getDisplaySizeV9(Display display, Point outSize) {
        outSize.x = display.getWidth();
        outSize.y = display.getHeight();
    }

    private static int getMobileNetworkType(NetworkInfo networkInfo) {
        switch (networkInfo.getSubtype()) {
            case 0:
            case 16:
            default:
                return 6;
            case 1:
            case 2:
                return 3;
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 14:
            case 15:
            case 17:
                return 4;
            case 13:
                return 5;
            case 18:
                return 2;
        }
    }

    static {
        SDK_INT = VERSION.SDK_INT;
        DEVICE = Build.DEVICE;
        MANUFACTURER = Build.MANUFACTURER;
        MODEL = Build.MODEL;
        DEVICE_DEBUG_INFO = DEVICE + ", " + MODEL + ", " + MANUFACTURER + ", " + SDK_INT;
        EMPTY_BYTE_ARRAY = new byte[0];
        XS_DATE_TIME_PATTERN = Pattern.compile("(\\d\\d\\d\\d)\\-(\\d\\d)\\-(\\d\\d)[Tt](\\d\\d):(\\d\\d):(\\d\\d)([\\.,](\\d+))?([Zz]|((\\+|\\-)(\\d?\\d):?(\\d\\d)))?");
        XS_DURATION_PATTERN = Pattern.compile("^(-)?P(([0-9]*)Y)?(([0-9]*)M)?(([0-9]*)D)?(T(([0-9]*)H)?(([0-9]*)M)?(([0-9.]*)S)?)?$");
        ESCAPED_CHARACTER_PATTERN = Pattern.compile("%([A-Fa-f0-9]{2})");
        CRC32_BYTES_MSBF = new int[]{0, 79764919, 159529838, 222504665, 319059676, 398814059, 445009330, 507990021, 638119352, 583659535, 797628118, 726387553, 890018660, 835552979, 1015980042, 944750013, 1276238704, 1221641927, 1167319070, 1095957929, 1595256236, 1540665371, 1452775106, 1381403509, 1780037320, 1859660671, 1671105958, 1733955601, 2031960084, 2111593891, 1889500026, 1952343757, -1742489888, -1662866601, -1851683442, -1788833735, -1960329156, -1880695413, -2103051438, -2040207643, -1104454824, -1159051537, -1213636554, -1284997759, -1389417084, -1444007885, -1532160278, -1603531939, -734892656, -789352409, -575645954, -646886583, -952755380, -1007220997, -827056094, -898286187, -231047128, -151282273, -71779514, -8804623, -515967244, -436212925, -390279782, -327299027, 881225847, 809987520, 1023691545, 969234094, 662832811, 591600412, 771767749, 717299826, 311336399, 374308984, 453813921, 533576470, 25881363, 88864420, 134795389, 214552010, 2023205639, 2086057648, 1897238633, 1976864222, 1804852699, 1867694188, 1645340341, 1724971778, 1587496639, 1516133128, 1461550545, 1406951526, 1302016099, 1230646740, 1142491917, 1087903418, -1398421865, -1469785312, -1524105735, -1578704818, -1079922613, -1151291908, -1239184603, -1293773166, -1968362705, -1905510760, -2094067647, -2014441994, -1716953613, -1654112188, -1876203875, -1796572374, -525066777, -462094256, -382327159, -302564546, -206542021, -143559028, -97365931, -17609246, -960696225, -1031934488, -817968335, -872425850, -709327229, -780559564, -600130067, -654598054, 1762451694, 1842216281, 1619975040, 1682949687, 2047383090, 2127137669, 1938468188, 2001449195, 1325665622, 1271206113, 1183200824, 1111960463, 1543535498, 1489069629, 1434599652, 1363369299, 622672798, 568075817, 748617968, 677256519, 907627842, 853037301, 1067152940, 995781531, 51762726, 131386257, 177728840, 240578815, 269590778, 349224269, 429104020, 491947555, -248556018, -168932423, -122852000, -60002089, -500490030, -420856475, -341238852, -278395381, -685261898, -739858943, -559578920, -630940305, -1004286614, -1058877219, -845023740, -916395085, -1119974018, -1174433591, -1262701040, -1333941337, -1371866206, -1426332139, -1481064244, -1552294533, -1690935098, -1611170447, -1833673816, -1770699233, -2009983462, -1930228819, -2119160460, -2056179517, 1569362073, 1498123566, 1409854455, 1355396672, 1317987909, 1246755826, 1192025387, 1137557660, 2072149281, 2135122070, 1912620623, 1992383480, 1753615357, 1816598090, 1627664531, 1707420964, 295390185, 358241886, 404320391, 483945776, 43990325, 106832002, 186451547, 266083308, 932423249, 861060070, 1041341759, 986742920, 613929101, 542559546, 756411363, 701822548, -978770311, -1050133554, -869589737, -924188512, -693284699, -764654318, -550540341, -605129092, -475935807, -413084042, -366743377, -287118056, -257573603, -194731862, -114850189, -35218492, -1984365303, -1921392450, -2143631769, -2063868976, -1698919467, -1635936670, -1824608069, -1744851700, -1347415887, -1418654458, -1506661409, -1561119128, -1129027987, -1200260134, -1254728445, -1309196108};
    }
}
