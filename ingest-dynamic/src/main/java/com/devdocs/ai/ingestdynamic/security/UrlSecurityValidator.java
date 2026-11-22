
package com.devdocs.ai.ingestdynamic.security;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

@Component
public class UrlSecurityValidator {

    private static final Set<String> ALLOWED_SCHEMES =
            Set.of("http", "https");

    public URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Website URL must not be blank."
            );
        }

        URI uri;
        try {
            uri = URI.create(url.trim()).normalize();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Website URL is invalid.", e
            );
        }

        String scheme = uri.getScheme();

        if (scheme == null
                || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Only HTTP and HTTPS URLs are supported."
            );
        }

        String host = uri.getHost();

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(
                    "Website URL must contain a valid host."
            );
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Website URL must not contain embedded credentials."
            );
        }

        validateHost(host);

        return uri;
    }

    private void validateHost(String host) {
        String normalizedHost = host.toLowerCase();

        if (normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")) {
            throw new IllegalArgumentException(
                    "Local network hosts are not allowed."
            );
        }

        InetAddress[] addresses;

        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(
                    "Website hostname could not be resolved.", e
            );
        }

        if (addresses.length == 0) {
            throw new IllegalArgumentException(
                    "Website hostname has no resolved addresses."
            );
        }

        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new IllegalArgumentException(
                        "Website URL resolves to a non-public IP address."
                );
            }
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();

        if (address instanceof Inet4Address) {
            return isPublicIpv4(bytes);
        }

        return isPublicIpv6(bytes);
    }

    private boolean isPublicIpv4(byte[] ip) {
        int first = ip[0] & 0xFF;
        int second = ip[1] & 0xFF;
        int third = ip[2] & 0xFF;

        // 0.0.0.0/8
        if (first == 0) return false;

        // 100.64.0.0/10 (Shared address space)
        if (first == 100 && second >= 64 && second <= 127) {
            return false;
        }

        // 192.0.0.0/24
        if (first == 192 && second == 0 && third == 0) {
            return false;
        }

        // 192.0.2.0/24 (Documentation)
        if (first == 192 && second == 0 && third == 2) {
            return false;
        }

        // 198.18.0.0/15 (Benchmark testing)
        if (first == 198 && (second == 18 || second == 19)) {
            return false;
        }

        // 198.51.100.0/24 (Documentation)
        if (first == 198 && second == 51 && third == 100) {
            return false;
        }

        // 203.0.113.0/24 (Documentation)
        if (first == 203 && second == 0 && third == 113) {
            return false;
        }

        // 240.0.0.0/4 (Reserved)
        if (first >= 240) {
            return false;
        }

        return true;
    }

    private boolean isPublicIpv6(byte[] ip) {
        int first = ip[0] & 0xFF;
        int second = ip[1] & 0xFF;

        // fc00::/7 (Unique local addresses)
        if ((first & 0xFE) == 0xFC) {
            return false;
        }

        // 2001:db8::/32 (Documentation)
        if (first == 0x20 && second == 0x01
                && (ip[2] & 0xFF) == 0x0D
                && (ip[3] & 0xFF) == 0xB8) {
            return false;
        }

        return true;
    }
}
