package com.wherefood.config;

import java.net.InetAddress;
import java.net.UnknownHostException;

final class IpAddress {
    private IpAddress() {}

    static String canonicalize(String value) {
        if (value == null || value.isBlank() || value.length() > 45) return null;
        boolean ipv4 = value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
        boolean ipv6 = value.indexOf(':') >= 0 && value.matches("[0-9a-fA-F:.]+");
        if (!ipv4 && !ipv6) return null;
        if (ipv4) {
            for (String octet : value.split("\\.")) {
                try {
                    if (Integer.parseInt(octet) > 255) return null;
                } catch (NumberFormatException exception) {
                    return null;
                }
            }
        }
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
