package fr.uga.im2ag.m1info.common;

public class TypeConverter {
    /** Converts the given value to the specified target type.
     *
     * @param value      The value to be converted.
     * @param targetType The class of the target type.
     * @param <T>        The target type.
     * @return The converted value of the target type, or null if conversion is not possible.
     * @throws IllegalArgumentException if the target type is unsupported.
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isInstance(value)) {
            return targetType.cast(value);
        }

        String stringValue = value.toString();

        try {
            if (targetType == Integer.class) {
                return (T) Integer.valueOf((int) Double.parseDouble(stringValue));
            }
            if (targetType == Long.class) {
                return (T) Long.valueOf(stringValue);
            }
            if (targetType == Double.class) {
                return (T) Double.valueOf(stringValue);
            }
            if (targetType == Boolean.class) {
                return (T) Boolean.valueOf(stringValue);
            }
            if (targetType == String.class) {
                return (T) stringValue;
            }
        } catch (NumberFormatException e) {
            return null;
        }

        throw new IllegalArgumentException("Unsupported type: " + targetType);
    }
}
