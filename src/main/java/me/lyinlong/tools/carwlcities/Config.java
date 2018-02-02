package me.lyinlong.tools.carwlcities;

public class Config {

    /**
     * 请求入口页
     */
    public static final String ROOT_URL = "http://www.stats.gov.cn/tjsj/tjbz/tjyqhdmhcxhfdm/2016/index.html";

    /**
     * 基本地址
     */
    public static final String ROOT_BASE_URL =  "http://www.stats.gov.cn/tjsj/tjbz/tjyqhdmhcxhfdm/2016/";

    /**
     * 保存的路径
     */
    public static final String SAVE_PATH = "/Users/ep/Downloads/areas/";

    /**
     * Cookie 的模板
     * 用于每次请求更换Cookie
     */
    public static final String COOKIE_TEMPLETE = "_gscu_${time}=07887521u4f9du11; _trs_uv=5ub7_6_j8ppaqh3; AD_RS_COOKIE=20080918";
}
