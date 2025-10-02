# 🔧 表格排序修复

**修复日期**：2025-10-02  
**问题**：扫描结果表格的序号列按字符串排序，导致排序错乱  
**状态**：✅ 已修复

---

## 🐛 问题描述

### 现象

扫描结果表格按序号排序时，顺序是：
```
22, 21, 20, 2, 19, 18, 17, 16, 15
```

这是**字符串排序**的结果（"2" < "19" < "20"），而不是期望的**数字排序**（2 < 19 < 20）。

### 预期行为

点击序号列排序时，应该按数字大小排序：
```
升序：2, 15, 16, 17, 18, 19, 20, 21, 22
降序：22, 21, 20, 19, 18, 17, 16, 15, 2
```

---

## 🔍 根本原因

**位置**：`LogModel.java`

**问题**：
- `AbstractTableModel` 的子类如果不重写 `getColumnClass()` 方法
- JTable 会默认将所有列视为 `Object.class`
- 排序时使用 `toString()` 进行比较
- 导致**字符串排序**而不是**数字排序**

**原代码**：
```java
public class LogModel extends AbstractTableModel {
    // ❌ 缺少 getColumnClass() 方法
    
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                return LogEntry.id;  // 返回 int，但被当作 Object
            // ...
        }
    }
}
```

**JTable 的默认行为**：
```java
// JTable 内部逻辑（简化）
Object value = model.getValueAt(row, column);
Class<?> columnClass = model.getColumnClass(column);

if (columnClass == Object.class) {
    // ❌ 使用 toString() 比较
    return value.toString().compareTo(otherValue.toString());
} else if (columnClass == Integer.class) {
    // ✅ 使用数字比较
    return ((Integer) value).compareTo((Integer) otherValue);
}
```

---

## ✅ 修复方案

### 修复内容

**文件**：`src/main/java/com/xprobe/scanner/Logs/LogModel.java`

**添加 `getColumnClass()` 方法**：

```java
/**
 * ✅ 重写此方法，让表格按正确类型排序
 * - 第0列（序号）：Integer 类型 → 数字排序
 * - 第4列（响应码）：Integer 类型 → 数字排序
 * - 第5列（响应长度）：Integer 类型 → 数字排序
 * - 其他列：String 类型 → 字符串排序
 */
@Override
public Class<?> getColumnClass(int columnIndex) {
    switch (columnIndex) {
        case 0:  // 序号
            return Integer.class;
        case 4:  // 响应码
            return Integer.class;
        case 5:  // 响应长度
            return Integer.class;
        default:
            return String.class;
    }
}
```

---

## 📊 修复前后对比

### 修复前（字符串排序）

```
点击序号列排序（升序）：
22, 21, 20, 2, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 1

原因：
"1" < "10" < "11" < "12" < ... < "2" < "20" < "21" < "22"
```

### 修复后（数字排序）

```
点击序号列排序（升序）：
1, 2, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22

原因：
1 < 2 < 10 < 11 < 12 < ... < 20 < 21 < 22 ✅
```

---

## 🎯 额外优化

### 同时修复了其他数字列

除了序号列，还修复了以下列的排序：

1. **响应码**（第4列）
   - 修复前：200, 201, 302, 404, 500 → "200" < "201" < "302" < "404" < "500"
   - 修复后：200 < 201 < 302 < 404 < 500 ✅

2. **响应长度**（第5列）
   - 修复前：1024, 2048, 512 → "1024" < "2048" < "512"
   - 修复后：512 < 1024 < 2048 ✅

---

## 🧪 测试验证

### 测试步骤

1. 启动 Burp Suite
2. 加载 XProbe 插件
3. 触发被动扫描，产生多条结果
4. 点击"序号"列头，进行排序
5. 验证排序结果

### 预期结果

**升序排序**：
```
1, 2, 3, 4, 5, ..., 10, 11, 12, ..., 100, 101, 102
```

**降序排序**：
```
102, 101, 100, ..., 12, 11, 10, ..., 5, 4, 3, 2, 1
```

---

## 📝 技术说明

### JTable 排序机制

JTable 使用 `TableRowSorter` 进行排序，排序逻辑如下：

1. **获取列类型**：调用 `model.getColumnClass(columnIndex)`
2. **获取比较器**：
   - 如果列类型是 `Integer.class`，使用数字比较器
   - 如果列类型是 `String.class`，使用字符串比较器
   - 如果列类型是 `Object.class`，使用 `toString()` 比较
3. **执行排序**：使用对应的比较器对行进行排序

### 为什么之前是字符串排序？

```java
// AbstractTableModel 的默认实现
public Class<?> getColumnClass(int columnIndex) {
    return Object.class;  // ❌ 默认返回 Object.class
}
```

如果不重写此方法，所有列都被视为 `Object.class`，导致：
- 数字列按字符串排序："2" < "10" < "100"
- 日期列按字符串排序："2024-01-02" < "2024-01-10"

---

## 🚀 最佳实践

### 建议

对于 JTable 的 `AbstractTableModel` 子类，**总是重写 `getColumnClass()` 方法**：

```java
@Override
public Class<?> getColumnClass(int columnIndex) {
    switch (columnIndex) {
        case INDEX_COLUMN:
            return Integer.class;    // 数字列
        case DATE_COLUMN:
            return Date.class;       // 日期列
        case BOOLEAN_COLUMN:
            return Boolean.class;    // 布尔列
        default:
            return String.class;     // 字符串列
    }
}
```

### 常见列类型

| 列类型 | 返回类型 | 排序行为 |
|--------|---------|---------|
| 数字（ID、计数、大小） | `Integer.class`<br>`Long.class`<br>`Double.class` | 数字排序 |
| 布尔值（开关、状态） | `Boolean.class` | false < true |
| 日期时间 | `Date.class`<br>`Instant.class` | 时间顺序 |
| 文本 | `String.class` | 字典序 |

---

## 📖 相关资源

- [Java JTable 官方文档](https://docs.oracle.com/javase/8/docs/api/javax/swing/JTable.html)
- [TableRowSorter 官方文档](https://docs.oracle.com/javase/8/docs/api/javax/swing/table/TableRowSorter.html)
- [AbstractTableModel 官方文档](https://docs.oracle.com/javase/8/docs/api/javax/swing/table/AbstractTableModel.html)

---

## 🎉 总结

| 项目 | 修复前 | 修复后 |
|------|--------|--------|
| 序号排序 | ❌ 字符串排序（22, 21, 20, 2, ...） | ✅ 数字排序（2, 20, 21, 22, ...） |
| 响应码排序 | ❌ 字符串排序 | ✅ 数字排序 |
| 响应长度排序 | ❌ 字符串排序 | ✅ 数字排序 |
| 代码修改 | - | 添加 `getColumnClass()` 方法 |
| 编译状态 | - | ✅ 编译成功 |
| 打包状态 | - | ✅ Jar包已生成 |

---

**修复完成时间**：2025-10-02  
**状态**：✅ 已修复并打包  
**影响范围**：扫描结果表格的所有数字列排序

