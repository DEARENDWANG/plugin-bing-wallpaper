package wang.dearend.bingwallpaper;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

// 出站地址校验：挡内网/环回/保留地址，防 SSRF
public final class OutboundGuard {

    private OutboundGuard() {
    }

    public static URI validate(String url) {
        final URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("接口地址格式无效");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("接口地址仅支持 http/https");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("接口地址缺少主机名");
        }
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("接口地址主机无法解析");
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
                throw new IllegalArgumentException("接口地址不允许指向内网或保留地址");
            }
        }
        return uri;
    }
}