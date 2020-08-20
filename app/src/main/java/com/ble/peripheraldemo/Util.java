package com.ble.peripheraldemo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Util {

    public static String timestamp(String pattern) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
    }

    public static String byteArrayToHex(byte[] arr) {
        if (arr == null || arr.length == 0) return "";

        StringBuilder sb = new StringBuilder();
        for (byte b : arr) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    public static byte[] hexToByteArray(String hex) {
        try {
            if (hex != null && hex.length() > 0) {
                if (hex.length() % 2 == 1) {
                    hex = "0" + hex;
                }
                byte[] arr = new byte[hex.length() / 2];
                for (int i = 0; i < arr.length; i++) {
                    int intValue = Integer.valueOf(hex.substring(i * 2, i * 2 + 2), 16);
                    arr[i] = (byte) intValue;
                }
                return arr;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
