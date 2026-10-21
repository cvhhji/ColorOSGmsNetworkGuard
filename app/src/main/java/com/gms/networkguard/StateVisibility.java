package com.gms.networkguard;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class StateVisibility {
    static final int DEBUG = 1;
    static final int ACCESSIBILITY = 2;
    static final int ALL = DEBUG | ACCESSIBILITY;

    static String hiddenValue(String key, int mask) {
        if (key == null) return null;
        switch (key) {
            case "development_settings_enabled":
            case "adb_enabled":
            case "adb_wifi_enabled":
                return (mask & DEBUG) != 0 ? "0" : null;
            case "accessibility_enabled":
            case "touch_exploration_enabled":
                return (mask & ACCESSIBILITY) != 0 ? "0" : null;
            case "enabled_accessibility_services":
            case "accessibility_shortcut_target_service":
            case "accessibility_button_targets":
            case "accessibility_qs_targets":
                return (mask & ACCESSIBILITY) != 0 ? "" : null;
            default:
                return null;
        }
    }

    static Bundle hideCallResult(String operation, String key, Bundle original, int mask) {
        if (operation.startsWith("GET_")) {
            String value = hiddenValue(key, mask);
            if (value == null) return original;
            Bundle result = new Bundle(original);
            result.putString("value", value);
            return result;
        }
        ArrayList<String> values = original.getStringArrayList("result_settings_list");
        if (values == null) return original;
        ArrayList<String> hidden = new ArrayList<>(values.size());
        for (String entry : values) {
            int delimiter = entry.indexOf('=');
            String name = delimiter < 0 ? entry : entry.substring(0, delimiter);
            String replacement = hiddenValue(name, mask);
            hidden.add(replacement == null ? entry : name + "=" + replacement);
        }
        Bundle result = new Bundle(original);
        result.putStringArrayList("result_settings_list", hidden);
        return result;
    }

    static Cursor hideCursor(Cursor original, boolean addedName, int mask) {
        try (Cursor source = original) {
            String[] columns = source.getColumnNames();
            int nameIndex = source.getColumnIndex("name");
            int valueIndex = source.getColumnIndex("value");
            int count = columns.length - (addedName ? 1 : 0);
            MatrixCursor result = new MatrixCursor(Arrays.copyOf(columns, count), source.getCount());
            while (source.moveToNext()) {
                String replacement = nameIndex < 0 ? null
                        : hiddenValue(source.getString(nameIndex), mask);
                Object[] row = new Object[count];
                for (int column = 0; column < count; column++) {
                    if (column == valueIndex && replacement != null) row[column] = replacement;
                    else switch (source.getType(column)) {
                        case Cursor.FIELD_TYPE_INTEGER: row[column] = source.getLong(column); break;
                        case Cursor.FIELD_TYPE_FLOAT: row[column] = source.getDouble(column); break;
                        case Cursor.FIELD_TYPE_BLOB: row[column] = source.getBlob(column); break;
                        case Cursor.FIELD_TYPE_STRING: row[column] = source.getString(column); break;
                        default: row[column] = null;
                    }
                }
                result.addRow(row);
            }
            Bundle extras = source.getExtras();
            if (extras != null) result.setExtras(new Bundle(extras));
            List<Uri> notifications = source.getNotificationUris();
            Context context = MainHook.currentContext();
            if (notifications != null && context != null) {
                result.setNotificationUris(context.getContentResolver(), notifications);
            }
            return result;
        }
    }
}
