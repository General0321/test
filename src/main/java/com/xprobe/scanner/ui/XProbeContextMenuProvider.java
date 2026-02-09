package com.xprobe.scanner.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.xprobe.scanner.core.OriginalResponseCache;
import com.xprobe.scanner.core.ScanTaskCollector;
import com.xprobe.scanner.core.TaskScheduler;
import com.xprobe.scanner.models.RequestContext;
import com.xprobe.scanner.models.ScanTask;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * XProbe 右键菜单提供者
 */
public class XProbeContextMenuProvider implements ContextMenuItemsProvider {
    private final MontoyaApi api;
    private final ScanTaskCollector scanTaskCollector;
    private final TaskScheduler taskScheduler;
    private final OriginalResponseCache responseCache;

    public XProbeContextMenuProvider(MontoyaApi api, ScanTaskCollector scanTaskCollector, 
                                    TaskScheduler taskScheduler, OriginalResponseCache responseCache) {
        this.api = api;
        this.scanTaskCollector = scanTaskCollector;
        this.taskScheduler = taskScheduler;
        this.responseCache = responseCache;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<Component> menuItems = new ArrayList<>();
        
        // 1. 获取选中的流量包（支持列表多选和编辑器单选）
        List<HttpRequestResponse> selectedItems = new ArrayList<>();
        
        // 尝试从列表中获取
        List<HttpRequestResponse> listSelection = event.selectedRequestResponses();
        if (listSelection != null && !listSelection.isEmpty()) {
            selectedItems.addAll(listSelection);
        } else {
            // 尝试从编辑器（如Repeater内部）获取
            Optional<MessageEditorHttpRequestResponse> editorSelection = event.messageEditorRequestResponse();
            editorSelection.ifPresent(messageEditorHttpRequestResponse -> 
                selectedItems.add(messageEditorHttpRequestResponse.requestResponse())
            );
        }

        // 如果没有任何选中项，不显示菜单
        if (selectedItems.isEmpty()) {
            return menuItems;
        }

        // 创建右键菜单项
        JMenuItem sendToXProbe = new JMenuItem("Send to XProbe");
        sendToXProbe.addActionListener(e -> {
            // 在新线程执行，避免阻塞 UI
            new Thread(() -> {
                for (HttpRequestResponse item : selectedItems) {
                    try {
                        if (item.request() == null) continue;

                        // 1. 缓存原始响应（如果有的话）
                        if (item.response() != null) {
                            responseCache.put(item.request(), item.response());
                        }

                        // 2. 创建上下文，明确标记 skipDeduplication = true
                        RequestContext context = new RequestContext(
                            "CONTEXT_MENU",
                            item.request().method(),
                            item.request().url(),
                            item.request().toString().hashCode(),
                            true // ✅ 强制验证，跳过去重
                        );

                        // 3. 收集扫描任务并执行
                        List<ScanTask> tasks = scanTaskCollector.collectScanTasks(item.request(), item.response(), context);
                        if (!tasks.isEmpty()) {
                            taskScheduler.scheduleScan(tasks);
                        }
                    } catch (Exception ex) {
                        // 忽略静默失败
                    }
                }
            }).start();
        });

        menuItems.add(sendToXProbe);
        return menuItems;
    }
}
