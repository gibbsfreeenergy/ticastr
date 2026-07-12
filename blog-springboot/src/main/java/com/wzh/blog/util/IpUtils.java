package com.wzh.blog.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import eu.bitwalker.useragentutils.UserAgent;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

/**
 * ip工具类
 *
 * @author 11921
 */
public class IpUtils {

    /**
     * 获取用户ip地址
     *
     * @param request 请求
     * @return ip地址
     */
    public static String getIpAddress(HttpServletRequest request) {
        String ipAddress = null;
        try {
            ipAddress = request.getHeader("x-forwarded-for");
            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getRemoteAddr();
            }
            // 对于通过多个代理的情况，第一个IP为客户端真实IP,多个IP按照','分割
            if (ipAddress != null && ipAddress.length() > 15) {
                // = 15
                if (ipAddress.indexOf(",") > 0) {
                    ipAddress = ipAddress.substring(0, ipAddress.indexOf(","));
                }
            }
        } catch (Exception e) {
            ipAddress = "";
        }
        return ipAddress;
    }

    /**
     * 解析ip地址
     *
     * @param ipAddress ip地址
     * @return 解析后的ip地址
     */
    public static String getIpSource(String ipAddress) {
        String endpoint = "https://opendata.baidu.com/api.php?query="
                + URLEncoder.encode(ipAddress, StandardCharsets.UTF_8)
                + "&co=&resource_id=6006&oe=utf8";
        try {
            URLConnection connection = URI.create(endpoint).toURL().openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                StringBuilder result = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                JSONObject response = JSON.parseObject(result.toString());
                JSONArray data = response.getJSONArray("data");
                if (data == null || data.isEmpty()) {
                    return "";
                }
                JSONObject location = data.getJSONObject(0);
                return location == null ? "" : location.getString("location");
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取访问设备
     *
     * @param request 请求
     * @return {@link UserAgent} 访问设备
     */
    public static UserAgent getUserAgent(HttpServletRequest request){
        return UserAgent.parseUserAgentString(request.getHeader("User-Agent"));
    }

}
