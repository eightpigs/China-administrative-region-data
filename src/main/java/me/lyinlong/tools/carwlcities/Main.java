package me.lyinlong.tools.carwlcities;

import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 爬取国家统计局的国家行政区域信息
 * 每一个城市都存为单独的JSON文件
 * http://www.stats.gov.cn/tjsj/tjbz/tjyqhdmhcxhfdm/2016/index.html
 *
 * 提示：
 *  1. 数据量太大，尽量少爬，可以使用已经爬完的数据
 *  2. 统计局每次更新后会及时更新数据
 *
 *  
 *
 * @author isme@lyinlong.me
 */
public class Main extends Thread {

    /**
     * 所有的城市数据
     */
    private static List<Item> provinces = new ArrayList<>();

    /**
     * 城市数据的Map，用于在线程中快速操作
     */
    private static Map<String, Item> provinceMap = new HashMap<>();

    /**
     * 当前请求地址
     * 因为统计局网站的每个请求地址都不是绝对的，所以需要记录每次请求的地址，供下次（该请求的子集）使用
     * 每个线程都有一个请求地址
     */
    private static ThreadLocal<String> currentUrl = new ThreadLocal<>();

    /**
     * 每个线程的Http对象
     * 与请求地址类似，每个线程使用不同的OkHttpClient
     */
    private static ThreadLocal<OkHttpClient> okHttpClientThreadLocal = new ThreadLocal<>();

    /**
     * 在解析表格中的Tr时，需要忽略的Class 内容
     */
    private static final Map<String, String> tableHeadMap = new HashMap<String, String>() {{
        put("cityhead", null);
        put("countyhead", null);
        put("townhead", null);
        put("villagehead", null);
    }};

    /**
     * 使用main方法加载出所有的省份，每个省份都使用一个单独的线程去下载
     */
    public static void main(String[] args) {
        try {

            okHttpClientThreadLocal.set(new OkHttpClient());

            String content = new Main().carwl(Config.ROOT_URL);

            okHttpClientThreadLocal.remove();

            Element table = getElements(content);

            getProvince(table);

            // 给线程命名为 省份的名称，然后在线程内部通过线程名称在provinceMap中取省份的具体信息进行抓取子集
            for (Item province : provinces) {
                Main main = new Main();
                System.out.println("启动抓取 [ " + province.getName() + " ] 的线程");
                main.setName(province.getName());
                provinceMap.put(province.getName(), province);
                main.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {

            OkHttpClient okHttpClient = okHttpClientThreadLocal.get();

            if (okHttpClient == null) {
                okHttpClient = new OkHttpClient();
                okHttpClientThreadLocal.set(okHttpClient);
            }

            // 获取当前线程对应的省份数据
            Item province = provinceMap.get(this.getName());

            // 该省份子集的请求地址
            currentUrl.set(province.getUrl());

            // 开始递归抓取本省以及子集
            String cityContent = carwl(province.getUrl());
            province.setItems(parse(getElements(cityContent)));

            currentUrl.remove();
            okHttpClientThreadLocal.remove();

            String json = JSONObject.toJSONString(province);
            FileWriter fileWriter = new FileWriter(Config.SAVE_PATH + province.getName() + ".json");
            fileWriter.write(json);
            fileWriter.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 发起请求抓取网页
     *
     * @param url 访问地址
     * @return
     * @throws Exception
     */
    private String carwl(String url) throws InterruptedException {
        Call call = okHttpClientThreadLocal.get().newCall(getRequset(url));
        try {

            Response response = call.execute();
            if (response.isSuccessful()) {
                ResponseBody body = response.body();
                byte[] bytes = body.bytes();
                body.close();
                response.close();
                return new String(bytes, "gb2312");
            }
        } catch (Exception e) {
            System.out.println(this.getName() + " => 访问超时，暂停 4s 后重新访问 " + e.getMessage());
            Thread.sleep(4000);
            return "-1";
        }
        return "";
    }

    /**
     * 生成新的Cookie
     * 每次请求都生成新的Cookie
     *
     * @return
     */
    private static String getCookie() {
        return Config.COOKIE_TEMPLETE.replace("${time}", String.valueOf(System.currentTimeMillis() / 1000));
    }

    /**
     * 获取请求对象
     *
     * @param url
     * @return
     */
    public static Request getRequset(String url) {
        return new Request.Builder()
                .url(url)
                .get()
                .headers(new Headers.Builder().add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36").build())
                .headers(new Headers.Builder().add("Cookie", getCookie()).build())
                .build();

    }

    /**
     * 获取数据的表格节点元素
     * <p>
     * 其中，因为网页使用表格布局，存在大量表格，所以获取到表格后，取第5个
     *
     * @param content HTML CODE
     * @return
     * @throws Exception
     */
    private static Element getElements(String content) throws Exception {
        if (content == null || content.length() == 0) {
            return null;
        }
        Document document = Jsoup.parse(content);
        Elements elements = document.getElementsByTag("table");

        return elements.get(4);
    }

    /**
     * 解析并递归获取子集
     * 1. 获取表格中的每一行
     * 2. 网页布局中，第一个单元格是行政区域编号，最后一个是名称，根据这个规律解析内容。
     * 3. 如果td包含a标签，也就是还有子集，继续取子集
     * 3.1 将当前的请求地址备份，取完子集后恢复当前请求地址，供下一个同级别内容使用
     *
     * @param table
     * @return
     * @throws Exception
     */
    private List<Item> parse(Element table) throws Exception {

        if (table != null) {

            List<Item> items = new ArrayList<>();

            Elements trs = table.getElementsByTag("tr");

            for (int i = 0; i < trs.size(); i++) {

                Element tr = trs.get(i);

                // 如果是需要跳过的表头
                if (tableHeadMap.containsKey(tr.className())) {
                    continue;
                }

                Elements tds = tr.getElementsByTag("td");

                Element code = tds.get(0);
                Element name = tds.get(tds.size() - 1);

                Item item = new Item();
                item.setCode(code.text());

                Elements childHref = name.getElementsByTag("a");

                // 如果td下面是一个超链接，代表还有子集，继续进入取子集
                if (childHref != null && childHref.size() > 0) {
                    item.setName(childHref.get(0).text());

                    String url = childHref.get(0).attr("href");

                    String currentUrlBak = new String(currentUrl.get());

                    String childrenUrl = currentUrl.get().substring(0, currentUrl.get().lastIndexOf("/")) + "/" + url;

                    System.out.println("----------- " + this.getName() + "  ------------");
                    System.out.println("" + item.getName());
                    System.out.println("        " + code.text());
                    System.out.println("        " + childrenUrl);
                    System.out.println("");

                    // 每次请求完成休眠1.5s
                    Thread.sleep(1500);

                    currentUrl.set(childrenUrl);
                    String content = carwl(childrenUrl);

                    // 请求一个都不能少
                    while ("-1".equals(content)) {
                        content = carwl(childrenUrl);
                    }

                    Element element = getElements(content);
                    item.setItems(parse(element));

                    // 抓取完成后更新会原地址
                    currentUrl.set(currentUrlBak);
                } else {
                    item.setName(name.text());
                    System.out.println("\t" + item.getName());
                    System.out.println("\t\t" + code.text());
                    System.out.println("\t\t 无子集");
                    System.out.println("");
                }

                items.add(item);
            }
            return items;
        }
        return null;
    }

    /**
     * 获取所有省份
     *
     * @param table
     */
    private static void getProvince(Element table) {
        Elements links = table.getElementsByTag("a");
        for (Element link : links) {
            String name = link.text();
            String url = link.attr("href");
            StringBuilder code = new StringBuilder(url.replace(".html", ""));
            int fill = 6 - code.length();
            for (int i = 0; i < fill; i++) {
                code.append("0");
            }
            Item item = new Item(name, code.toString(), new ArrayList<>());

            String childrenUrl = Config.ROOT_BASE_URL + url;

            item.setUrl(childrenUrl);
            provinces.add(item);
        }
    }
}
