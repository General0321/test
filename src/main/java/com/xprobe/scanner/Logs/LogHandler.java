package com.xprobe.scanner.Logs;


import burp.api.montoya.http.handler.*;

public class LogHandler implements HttpHandler
{
    private final LogModel logModel;
    // 暂时使用ThreadLocal，虽然不是最理想的解决方案
    private final ThreadLocal<Long> requestStartTime = new ThreadLocal<>();

    public LogHandler(LogModel logModel)
    {

        this.logModel = logModel;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent)
    {
        requestStartTime.set(System.currentTimeMillis()); // 记录请求开始时间
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived)
    {
        long responseEndTime = System.currentTimeMillis(); // 记录响应结束时间
        Long startTime = requestStartTime.get();
        
        if (startTime != null) {
            //logModel.add(responseReceived, startTime, responseEndTime);
            requestStartTime.remove(); // 清理ThreadLocal
        }

//        if (responseReceived.headers().contains("X-Match-Found")) {
//            long responseEndTime = System.currentTimeMillis(); // 记录响应结束时间
//            logModel.add(responseReceived, requestStartTime, responseEndTime);
//        }

        return ResponseReceivedAction.continueWith(responseReceived);
    }

}