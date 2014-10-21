package com.cmbc.configserver.common.protocol;

/**
 * the response code
 */
public class ResponseCode {
    public static final int PUBLISH_CONFIG_OK = 1;
    public static final int PUBLISH_CONFIG_FAILED = 2;
    public static final int UNPUBLISH_CONFIG_OK = 3;
    public static final int UNPUBLISH_CONFIG_FAILED = 4;
    public static final int HEARTBEAT_OK = 5;
    public static final int HEARTBEAT_FAILED = 6;
}