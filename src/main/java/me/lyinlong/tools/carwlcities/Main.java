package me.lyinlong.tools.carwlcities;

import com.alibaba.fastjson.JSONArray;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static OkHttpClient okHttpClient;

    private static String cookieTemplete = "_gscu_${time}=07887521u4f9du11; _trs_uv=5ub7_6_j8ppaqh3; AD_RS_COOKIE=20080918";

    public static void main(String[] args) {
        try {
            okHttpClient = new OkHttpClient();
            String content = carwl(Config.ROOT_URL);
            List<Item> items = parse(getElements(content));

            String s = JSONArray.toJSONString(items);
            FileWriter fileWriter = new FileWriter("/Users/ep/Downloads/json.json");
            fileWriter.write(s);
            fileWriter.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String carwl(String url) throws Exception {
        Call call = okHttpClient.newCall(getRequset(url));
        try {

            Response response = call.execute();
            if (response.isSuccessful()) {
                ResponseBody body = response.body();
                byte[] bytes = body.bytes();
                response.close();
                body.close();
                return new String(bytes, "gb2312");
            }
        } catch (Exception e) {
            System.out.println("访问超时，暂停 5s 后重新访问 ");
            e.printStackTrace();
            Thread.sleep(5000);
            carwl(url);
        }
        return "";
    }

    private static String getCookie() {
        return
                cookieTemplete.replace("${time}", String.valueOf(System.currentTimeMillis() / 1000));
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

    public static Element getElements(String content) throws Exception {
        if (content == null || content.length() == 0) {
            return null;
        }
        Document document = Jsoup.parse(content);
        Elements elements = document.getElementsByTag("table");

        return elements.get(3);
    }

    public static List<Item> parse(Element root) throws Exception {
        Element table = root;
        if (table == null)
            return null;
        Elements links = table.getElementsByTag("a");

        List<Item> items = new ArrayList<>();

        for (int i = 0; i < links.size(); i++) {
            Element link = links.get(i);

            // 如果是编码的内容，跳过
            if (link.text().contains("0"))
                continue;

            Item item = new Item(link.text(), "", new ArrayList<>());

            if (link.hasAttr("href")) {

                String url = link.attr("href");
                String code = url.replace(".html", "");

                System.out.println(link.text());

                if (code.contains("/"))
                    code = code.substring(code.indexOf("/") + 1);

                item.setCode(code);

                String childrenUrl = Config.ROOT_BASE_URL + getParentUrl(url) + ".html";

                System.out.println("\t" + code);
                System.out.println("\t" + childrenUrl);

                int num = 800 + (int) (Math.random() * (2000 - 800 + 1));

                Thread.sleep(num);

                String content = carwl(childrenUrl);
                Element element = getElements(content);
                item.setItems(parse(element));

                System.out.println("\n-------------------------------------------------\n");
            }

        }

        return items;
    }

    private static String getParentUrl(String url) {
        int i = url.lastIndexOf(".");
        return url.substring(0, i);
    }
}
