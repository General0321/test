# 🔧 智能滚动修复

**修复日期**：2025-10-02  
**问题**：按序号排序后查看历史记录时，新流量到来会自动滚动到底部，打断查看  
**状态**：✅ 已修复

---

## 🐛 问题描述

### 现象

1. 用户按序号**倒序排序**，想查看最早的记录（序号小的在底部）
2. 用户滚动到底部，查看序号1、2、3的记录
3. 新流量到来（序号100、101、102）
4. 表格自动滚动到**末尾**（新插入的行）
5. ❌ 用户被打断，无法继续查看序号1、2、3

### 预期行为

- 如果用户在**顶部**查看历史记录 → **不要自动滚动**
- 如果用户在**底部**查看最新记录 → **自动滚动到最新**

---

## 🔍 根本原因

**位置**：`ScanResultTab.java:294-307`

**原代码**：
```java
// ✅ 自动滚动到最新记录（如果是新增行）
if (e.getType() == javax.swing.event.TableModelEvent.INSERT) {
    SwingUtilities.invokeLater(() -> {
        try {
            int lastRow = resultTable.getRowCount() - 1;
            if (lastRow >= 0) {
                // ❌ 无条件滚动到最后一行
                resultTable.scrollRectToVisible(resultTable.getCellRect(lastRow, 0, true));
            }
        } catch (Exception ex) {
            // 忽略滚动错误
        }
    });
}
```

**问题**：
- 无论用户在哪个位置，新行插入时都会**强制滚动到最后一行**
- 导致用户查看历史记录时被打断

---

## ✅ 修复方案

### 智能滚动逻辑

**核心思想**：只有当用户**已经在底部**时，才自动滚动到新记录

**实现**：
```java
// ✅ 智能滚动：只有当用户在底部时，才自动滚动到最新记录
if (e.getType() == javax.swing.event.TableModelEvent.INSERT) {
    SwingUtilities.invokeLater(() -> {
        try {
            int lastRow = resultTable.getRowCount() - 1;
            if (lastRow < 0) return;
            
            // 获取滚动面板
            JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, resultTable);
            if (scrollPane == null) return;
            
            JViewport viewport = scrollPane.getViewport();
            Rectangle viewRect = viewport.getViewRect();
            Rectangle tableRect = resultTable.getBounds();
            
            // ✅ 判断用户是否在底部附近（距离底部小于100像素）
            boolean isNearBottom = (viewRect.y + viewRect.height) >= (tableRect.height - 100);
            
            // ✅ 只有在底部时，才自动滚动
            if (isNearBottom) {
                resultTable.scrollRectToVisible(resultTable.getCellRect(lastRow, 0, true));
            }
        } catch (Exception ex) {
            // 忽略滚动错误
        }
    });
}
```

---

## 📊 修复前后对比

### 修复前

```
场景1：用户在顶部查看历史记录
用户位置：序号1、2、3（倒序排序）
新流量：序号100、101、102
❌ 自动滚动到底部（新记录）
❌ 用户被打断，无法继续查看历史

场景2：用户在底部查看最新记录
用户位置：序号98、99、100
新流量：序号101、102、103
✅ 自动滚动到底部（新记录）
✅ 用户可以看到最新流量
```

### 修复后

```
场景1：用户在顶部查看历史记录
用户位置：序号1、2、3（倒序排序）
新流量：序号100、101、102
✅ 不自动滚动
✅ 用户可以继续查看历史

场景2：用户在底部查看最新记录
用户位置：序号98、99、100（距离底部<100px）
新流量：序号101、102、103
✅ 自动滚动到底部（新记录）
✅ 用户可以看到最新流量
```

---

## 🎯 判断逻辑

### 如何判断用户是否在底部？

```java
// 获取视口（用户可见区域）
JViewport viewport = scrollPane.getViewport();
Rectangle viewRect = viewport.getViewRect();  // 用户可见的矩形区域

// 获取表格总大小
Rectangle tableRect = resultTable.getBounds();  // 表格总大小

// 计算用户可见区域的底部Y坐标
int visibleBottom = viewRect.y + viewRect.height;

// 表格的总高度
int tableHeight = tableRect.height;

// 判断：如果可见区域底部 >= 表格总高度 - 100px
boolean isNearBottom = visibleBottom >= (tableHeight - 100);
```

**阈值**：100像素
- 如果用户在底部100像素以内 → 认为在底部，自动滚动
- 如果用户在底部100像素以外 → 认为不在底部，不滚动

---

## 📝 技术说明

### Swing 组件层次

```
JScrollPane (滚动面板)
    └─ JViewport (视口，控制可见区域)
           └─ JTable (表格)
```

**关键API**：
- `JViewport.getViewRect()`：获取用户当前可见的矩形区域
- `Component.getBounds()`：获取组件的总大小
- `SwingUtilities.getAncestorOfClass()`：向上查找父组件

---

## 🧪 测试场景

### 场景1：倒序排序，查看历史记录

**操作**：
1. 点击序号列，倒序排序（大的在顶部）
2. 滚动到底部，查看序号1、2、3
3. 等待新流量到来

**预期结果**：
- ✅ 表格不自动滚动
- ✅ 用户仍然停留在底部，可以继续查看序号1、2、3

---

### 场景2：升序排序，在顶部查看

**操作**：
1. 点击序号列，升序排序（小的在顶部）
2. 停留在顶部，查看序号1、2、3
3. 等待新流量到来

**预期结果**：
- ✅ 表格不自动滚动
- ✅ 用户仍然停留在顶部，可以继续查看序号1、2、3

---

### 场景3：在底部查看最新记录

**操作**：
1. 点击序号列，升序排序（小的在顶部）
2. 滚动到底部，查看最新记录
3. 等待新流量到来

**预期结果**：
- ✅ 表格自动滚动到最新记录
- ✅ 用户可以持续看到最新流量

---

### 场景4：中间位置查看

**操作**：
1. 滚动到中间位置（序号50左右）
2. 等待新流量到来

**预期结果**：
- ✅ 表格不自动滚动
- ✅ 用户停留在中间位置

---

## 🎨 用户体验改进

### 改进前

```
用户想查看某个历史流量包
    ↓
滚动到该位置（比如序号5）
    ↓
正在查看请求/响应详情
    ↓
新流量到来
    ↓
❌ 表格自动滚动到底部（序号100）
    ↓
❌ 用户被打断，需要重新滚动回去
    ↓
❌ 用户体验差
```

### 改进后

```
用户想查看某个历史流量包
    ↓
滚动到该位置（比如序号5）
    ↓
正在查看请求/响应详情
    ↓
新流量到来
    ↓
✅ 表格不滚动，用户停留在原位置
    ↓
✅ 用户可以继续查看，不被打断
    ↓
✅ 用户体验好
```

---

## 📖 类似问题的解决方案

### 问题：聊天应用的消息滚动

**需求**：
- 用户在底部：自动滚动到新消息
- 用户在查看历史消息：不自动滚动

**解决方案**：与本修复相同
```java
boolean isNearBottom = (viewRect.y + viewRect.height) >= (tableRect.height - threshold);
if (isNearBottom) {
    scrollToBottom();
}
```

---

### 问题：日志查看器的滚动

**需求**：
- 用户在底部：自动滚动到新日志
- 用户在查看旧日志：不自动滚动

**解决方案**：与本修复相同

---

## 🎯 总结

| 项目 | 修复前 | 修复后 |
|------|--------|--------|
| 用户在顶部查看历史 | ❌ 被自动滚动打断 | ✅ 不滚动，可继续查看 |
| 用户在中间查看 | ❌ 被自动滚动打断 | ✅ 不滚动，可继续查看 |
| 用户在底部查看最新 | ✅ 自动滚动（正确） | ✅ 自动滚动（保持） |
| 代码修改 | - | 添加智能滚动判断 |
| 编译状态 | - | ✅ 编译成功 |
| 打包状态 | - | ✅ Jar包已生成 |

---

## 🚀 使用方法

1. 重新加载插件到 Burp
2. 倒序排序，滚动到底部查看早期记录
3. 等待新流量
4. ✅ 表格不会自动滚动，可以继续查看历史

---

**修复完成时间**：2025-10-02  
**状态**：✅ 已修复并打包  
**影响范围**：扫描结果表格的自动滚动行为

