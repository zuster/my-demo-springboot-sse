package cn.zuster.sse;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * SSE 并发测试
 *
 * @author songyh
 * @date 2021/1/8
 */
public class SseConcurrentClient {
    public static void main(String[] args) throws IOException, InterruptedException {
        concurrent();
    }

    /**
     * 获取SSE输入流。
     *
     * @param urlPath
     * @return
     * @throws IOException
     */
    public static InputStream getSseInputStream(String urlPath) throws IOException {
        URL url = new URL(urlPath);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        // 这儿根据自己的情况选择get或post
        urlConnection.setRequestMethod("GET");
        urlConnection.setDoOutput(true);
        urlConnection.setDoInput(true);
        urlConnection.setUseCaches(false);
        urlConnection.setRequestProperty("Connection", "Keep-Alive");
        urlConnection.setRequestProperty("Charset", "UTF-8");
        //读取过期时间（很重要，建议加上）
        urlConnection.setReadTimeout(60 * 1000);
        // text/plain模式
        urlConnection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
        InputStream inputStream = urlConnection.getInputStream();
        InputStream is = new BufferedInputStream(inputStream);
        return is;
    }

    /**
     * 读取数据
     *
     * @param is
     * @throws IOException
     */
    public static void readStream(InputStream is) throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line = "";
            while ((line = reader.readLine()) != null) {
                // 处理数据接口
                System.out.println(line);
            }
            // 当服务器端主动关闭的时候，客户端无法获取到信号。现在还不清楚原因。所以无法执行的此处。
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOException("关闭数据流！");
        }
    }

    /**
     * 多线程执行
     *
     * @throws InterruptedException
     */
    public static void concurrent() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Thread thread = new Thread(() -> {
                try {
                    String urlPath = "http://localhost:8080/sse/start?clientId=" + System.currentTimeMillis();
                    InputStream inputStream = getSseInputStream(urlPath);
                    readStream(inputStream);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            thread.start();
            Thread.sleep(100);
        }
    }

}
