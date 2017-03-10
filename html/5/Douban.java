package com.wz.spider.client;

import com.wz.spider.network.NetClient;
import com.wz.spider.util.FileUtil;
import com.wz.spider.util.ImageUtil;
import com.wz.spider.util.StringUtil;
import com.wz.spider.util.UrlUtil;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Douban {

    // 需要爬取的页面数量
    private static final int CNT = 5;
    private static final String RES_PATH = "D:\\res.csv";
    private static final String CAPTCHA_PATH = "D:\\captcha.jpg";
    // 出错等待时间
    private static final int SLEEP_TIME = 10;
    private static Queue<String> sPageUrls = new LinkedList<String>();
    private static HashMap<String, String> sDetailUrls = new HashMap<String, String>();
    private static List<String> sCheckItems = new ArrayList<String>();
    private static HashMap<String, String> sCheckedRes = new HashMap<String, String>();
    private static HashSet<String> sVisitedUrls = new HashSet<String>();
    private static ExecutorService sThreadPool;
    private static Scanner sScanner;

    public static void main(String[] args) {

        // TODO Auto-generated method stub
        init();
        while (sPageUrls.size() > 0) {
            String pageUrl = sPageUrls.poll();
            getDetailPages(pageUrl);
        }

        Set<Entry<String, String>> entries = sDetailUrls.entrySet();
        for (Iterator<Entry<String, String>> iterator = entries.iterator(); iterator.hasNext(); ) {
            Entry<String, String> entry = iterator.next();
            String title = entry.getKey();
            String url = entry.getValue();
            // visitedDetailUrl( title, url );
            sThreadPool.execute(new VisitDetailPageThread(url, title));
        }
        int checkTimes = 0;
        // 执行一次，不再接收新的任务，继续执行之前的任务
        sThreadPool.shutdown();
        try {
            while (!sThreadPool.awaitTermination(10, TimeUnit.SECONDS) && checkTimes < 100) {
                // 每十秒检查一次线程是否执行完毕
                System.out.println("尚未全部爬取完毕！");
                checkTimes++;
            }
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            sThreadPool.shutdownNow();
            e.printStackTrace();
        }
        sThreadPool.shutdownNow();
        System.out.println("回归主线程--->");

        if (sCheckedRes.size() > 0) {
            Set<Entry<String, String>> resSet = sCheckedRes.entrySet();
            for (Iterator<Entry<String, String>> iterator = resSet.iterator(); iterator.hasNext(); ) {
                Entry<String, String> entry = iterator.next();
                System.out.println("符合要求--->" + entry.getKey() + "，地址--->" + entry.getValue());
            }
            FileUtil.writeCSV(RES_PATH, sCheckedRes, true, "GBK");
        }
        if (sScanner != null) {
            sScanner.close();
        }
        System.out.println("爬取完毕！");
    }

    private static void init() {

        // 以下设置为了Fiddler抓包使用
        // System.setProperty( "http.proxyHost", "localhost" );
        // System.setProperty( "http.proxyPort", "8888" );
        // System.setProperty( "https.proxyHost", "localhost" );
        // System.setProperty( "https.proxyPort", "8888" );
        sThreadPool = Executors.newFixedThreadPool(10);
        sScanner = new Scanner(System.in);
        System.out.println("请输入要检查的关键词，以'#'分隔：");
        String input = sScanner.next();
        if (input == null) {
            System.out.println("未获取需要检查的关键词，程序退出！");
            System.exit(-1);
        }
        String[] tmps = input.split("#");
        if (tmps == null || tmps.length <= 0) {
            System.out.println("未获取需要检查的关键词，程序退出！");
            System.exit(-1);
        } else {
            System.out.println(Arrays.toString(tmps));
            for (String tmp : tmps) {
                sCheckItems.add(tmp);
            }
        }
        FileUtil.createNewFile(RES_PATH, true);
        FileUtil.writeCSV(RES_PATH, "标题", "链接", true, "GBK");
        String prefixUrl = "https://www.douban.com/group/26926/discussion?start=";
        for (int i = 0; i < CNT; i++) {
            String url = prefixUrl + 25 * i;
            sPageUrls.add(url);
        }
    }

    private static void getDetailPages(String pageUrl) {

        System.out.println("正在爬取页面--->" + pageUrl);
        sVisitedUrls.add(pageUrl);
        Response response = NetClient.doGet(pageUrl);
        if (response != null && response.isSuccessful()) {

            String html = StringUtil.responseToString(response);
            Document document = Jsoup.parse(html);
            Elements elements = document.select("td[class=title]");
            if (elements != null && elements.size() > 0) {
                for (Element element : elements) {
                    Elements aElements = element.getElementsByTag("a");
                    String title = aElements.text();
                    String href = aElements.attr("href");
                    String detailUrl = UrlUtil.getAbsoluteUrl(pageUrl, href);
                    if (checkString(title) && !sCheckedRes.containsValue(detailUrl)) {
                        sCheckedRes.put(title, detailUrl);
                    } else {
                        if (!sDetailUrls.containsValue(detailUrl) && !sVisitedUrls.contains(detailUrl)) {
                            sDetailUrls.put(title, detailUrl);
                        }
                    }
                }
            }
            response.body().close();
        } else if (response != null && 403 == response.code()) {
            // 此时服务器禁止访问
            System.out.println("休眠" + SLEEP_TIME + "s！");
            for (int i = SLEEP_TIME; i >= 0; i--) {
                System.out.println("休眠中--->" + i + "s");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            processRobotCheckPage();
            getDetailPages(pageUrl);
        }
    }

    private static void visitedDetailUrl(String title, String detailUrl) {

        System.out.println("正在爬取详情页面--->" + detailUrl);
        sVisitedUrls.add(detailUrl);
        Response response = NetClient.doGet(detailUrl);
        if (response != null && response.isSuccessful()) {
            String html = StringUtil.responseToString(response);
            Document document = Jsoup.parse(html);
            Elements elements = document.select("div[class=topic-content] > p");
            String content = elements.text();
            if (checkString(content) && !sCheckedRes.containsValue(detailUrl)) {
                sCheckedRes.put(title, detailUrl);
            }
        }
        response.body().close();
    }

    /**
     * processRobotCheckPage 处理异常情况，豆瓣会验证机器人
     *
     * @throws @since 3.1.0
     * @permission void
     * @api 5
     */
    private static void processRobotCheckPage() {

        String checkUrl = "https://www.douban.com/misc/sorry";
        System.out.println("处理异常情况--->" + checkUrl);
        Response response = NetClient.doGet(checkUrl);
        if (response != null) {
            String html = StringUtil.responseToString(response);
            Document document = Jsoup.parse(html);
            Elements img = document.select("img[alt=captcha]");
            String src = img.attr("src");
            String id = document.select("input[name=captcha-id]").attr("value");
            HashMap<String, String> params = new HashMap<String, String>();
            // params.put( "ck", "5txN" );
            Response imgResponse = NetClient.doGet(src);
            if (imgResponse != null && imgResponse.isSuccessful()) {
                FileUtil.saveInputStreamToFile(CAPTCHA_PATH, imgResponse.body().byteStream());
            }
            ImageUtil.showPic(CAPTCHA_PATH);
            System.out.println("请输入图形验证码：");
            String code = sScanner.next();
            params.put("captcha-id", id);
            params.put("captcha-solution", code);
            Response checkResponse = NetClient.doPost(checkUrl, params);
            if (checkResponse != null && 403 != checkResponse.code()) {
                System.out.println("验证成功！");
            } else {
                System.out.println("验证失败--->" + checkResponse.code());
            }
            response.body().close();
            imgResponse.body().close();
        }
    }

    private static boolean checkString(String str) {

        if (str == null || str == "") {
            return false;
        }

        for (String checkItem : sCheckItems) {
            if (str.contains(checkItem)) {
                return true;
            }
        }
        return false;
    }

    private static class VisitDetailPageThread extends Thread {

        private String mUrl;
        private String mTitle;

        public VisitDetailPageThread(String url, String title) {

            mTitle = title;
            mUrl = url;
        }

        @Override
        public void run() {

            // TODO Auto-generated method stub
            visitedDetailUrl(mTitle, mUrl);
        }
    }

}
