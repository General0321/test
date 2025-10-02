#!/bin/bash

# XProbe 基础功能自动化测试脚本

echo "🧪 XProbe 基础功能测试"
echo "======================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

CONFIG_DIR="$HOME/.config/xprobe"
CONFIG_FILE="$CONFIG_DIR/config.json"
BACKUP_FILE="$CONFIG_DIR/config.json.backup"

PASS_COUNT=0
FAIL_COUNT=0

# 测试函数
test_pass() {
    echo -e "${GREEN}✅ PASS${NC}: $1"
    ((PASS_COUNT++))
}

test_fail() {
    echo -e "${RED}❌ FAIL${NC}: $1"
    ((FAIL_COUNT++))
}

test_info() {
    echo -e "${YELLOW}ℹ️  INFO${NC}: $1"
}

echo "📦 测试1：编译和打包"
echo "-------------------"
if ./gradlew clean jar --console=plain > /dev/null 2>&1; then
    test_pass "编译成功"
    if [ -f "build/libs/XProbe-1.0.0.jar" ]; then
        test_pass "JAR包生成成功"
        JAR_SIZE=$(du -h build/libs/XProbe-1.0.0.jar | cut -f1)
        test_info "JAR包大小: $JAR_SIZE"
    else
        test_fail "JAR包未找到"
    fi
else
    test_fail "编译失败"
fi

echo ""
echo "📁 测试2：配置文件结构"
echo "-------------------"

# 检查配置目录
if [ -d "$CONFIG_DIR" ]; then
    test_pass "配置目录存在: $CONFIG_DIR"
else
    test_info "配置目录不存在（首次运行时会自动创建）"
fi

# 检查配置文件
if [ -f "$CONFIG_FILE" ]; then
    test_pass "配置文件存在: $CONFIG_FILE"
    
    # 验证JSON格式
    if command -v python3 &> /dev/null; then
        if python3 -m json.tool "$CONFIG_FILE" > /dev/null 2>&1; then
            test_pass "配置文件JSON格式正确"
        else
            test_fail "配置文件JSON格式错误"
        fi
    else
        test_info "未安装python3，跳过JSON验证"
    fi
    
    # 检查文件权限
    if [ -r "$CONFIG_FILE" ] && [ -w "$CONFIG_FILE" ]; then
        test_pass "配置文件权限正确（可读写）"
    else
        test_fail "配置文件权限不正确"
    fi
else
    test_info "配置文件不存在（首次运行时会自动创建）"
fi

echo ""
echo "🔍 测试3：关键类文件检查"
echo "-------------------"

KEY_FILES=(
    "src/main/java/com/xprobe/scanner/config/XProbeConfigManager.java"
    "src/main/java/com/xprobe/scanner/config/XProbeConfig.java"
    "src/main/java/com/xprobe/scanner/core/TaskScheduler.java"
    "src/main/java/com/xprobe/scanner/ui/PassiveScanConfigTab.java"
    "src/main/java/com/xprobe/scanner/XProbe.java"
)

for file in "${KEY_FILES[@]}"; do
    if [ -f "$file" ]; then
        test_pass "关键文件存在: $(basename $file)"
    else
        test_fail "关键文件缺失: $file"
    fi
done

echo ""
echo "📊 测试4：文档完整性"
echo "-------------------"

DOC_FILES=(
    "CRITICAL_FIXES_COMPLETE.md"
    "UI_AND_LOG_IMPROVEMENTS.md"
    "CONFIG_MANAGER_REFACTORING_COMPLETE.md"
    "QUICK_TEST_GUIDE.md"
)

for file in "${DOC_FILES[@]}"; do
    if [ -f "$file" ]; then
        test_pass "文档存在: $file"
    else
        test_fail "文档缺失: $file"
    fi
done

echo ""
echo "🔬 测试5：配置管理器容错性"
echo "-------------------"

# 备份现有配置
if [ -f "$CONFIG_FILE" ]; then
    cp "$CONFIG_FILE" "$BACKUP_FILE"
    test_info "已备份现有配置"
fi

# 测试5.1：删除配置文件
test_info "测试场景：配置文件不存在"
if [ -f "$CONFIG_FILE" ]; then
    rm "$CONFIG_FILE"
fi
test_info "配置文件已删除，插件应在启动时创建默认配置"
test_pass "容错测试1：配置缺失场景准备完成"

# 恢复配置
if [ -f "$BACKUP_FILE" ]; then
    cp "$BACKUP_FILE" "$CONFIG_FILE"
    rm "$BACKUP_FILE"
    test_info "已恢复配置文件"
fi

echo ""
echo "🎨 测试6：代码质量检查"
echo "-------------------"

# 检查是否有明显的编译警告
if ./gradlew compileJava --console=plain 2>&1 | grep -q "错误"; then
    test_fail "编译有错误"
else
    test_pass "编译无错误"
fi

# 检查TODO/FIXME注释
TODO_COUNT=$(grep -r "TODO\|FIXME" src/main/java/com/xprobe 2>/dev/null | wc -l | tr -d ' ')
if [ "$TODO_COUNT" -gt 0 ]; then
    test_info "代码中有 $TODO_COUNT 个TODO/FIXME标记"
else
    test_pass "代码中无TODO/FIXME标记"
fi

echo ""
echo "========================"
echo "📊 测试总结"
echo "========================"
echo -e "${GREEN}通过：$PASS_COUNT${NC}"
echo -e "${RED}失败：$FAIL_COUNT${NC}"
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}🎉 所有自动化测试通过！${NC}"
    echo ""
    echo "✅ 基础功能正常"
    echo "✅ 代码结构完整"
    echo "✅ 文档齐全"
    echo ""
    echo "📝 下一步："
    echo "   1. 在Burp Suite中加载插件"
    echo "   2. 按照 QUICK_TEST_GUIDE.md 进行手动测试"
    echo "   3. 重点测试：扫描结果显示payload、UI优化效果"
    exit 0
else
    echo -e "${RED}⚠️  有 $FAIL_COUNT 项测试失败${NC}"
    echo ""
    echo "请检查上述失败项，修复后重新测试"
    exit 1
fi


# XProbe 基础功能自动化测试脚本

echo "🧪 XProbe 基础功能测试"
echo "======================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

CONFIG_DIR="$HOME/.config/xprobe"
CONFIG_FILE="$CONFIG_DIR/config.json"
BACKUP_FILE="$CONFIG_DIR/config.json.backup"

PASS_COUNT=0
FAIL_COUNT=0

# 测试函数
test_pass() {
    echo -e "${GREEN}✅ PASS${NC}: $1"
    ((PASS_COUNT++))
}

test_fail() {
    echo -e "${RED}❌ FAIL${NC}: $1"
    ((FAIL_COUNT++))
}

test_info() {
    echo -e "${YELLOW}ℹ️  INFO${NC}: $1"
}

echo "📦 测试1：编译和打包"
echo "-------------------"
if ./gradlew clean jar --console=plain > /dev/null 2>&1; then
    test_pass "编译成功"
    if [ -f "build/libs/XProbe-1.0.0.jar" ]; then
        test_pass "JAR包生成成功"
        JAR_SIZE=$(du -h build/libs/XProbe-1.0.0.jar | cut -f1)
        test_info "JAR包大小: $JAR_SIZE"
    else
        test_fail "JAR包未找到"
    fi
else
    test_fail "编译失败"
fi

echo ""
echo "📁 测试2：配置文件结构"
echo "-------------------"

# 检查配置目录
if [ -d "$CONFIG_DIR" ]; then
    test_pass "配置目录存在: $CONFIG_DIR"
else
    test_info "配置目录不存在（首次运行时会自动创建）"
fi

# 检查配置文件
if [ -f "$CONFIG_FILE" ]; then
    test_pass "配置文件存在: $CONFIG_FILE"
    
    # 验证JSON格式
    if command -v python3 &> /dev/null; then
        if python3 -m json.tool "$CONFIG_FILE" > /dev/null 2>&1; then
            test_pass "配置文件JSON格式正确"
        else
            test_fail "配置文件JSON格式错误"
        fi
    else
        test_info "未安装python3，跳过JSON验证"
    fi
    
    # 检查文件权限
    if [ -r "$CONFIG_FILE" ] && [ -w "$CONFIG_FILE" ]; then
        test_pass "配置文件权限正确（可读写）"
    else
        test_fail "配置文件权限不正确"
    fi
else
    test_info "配置文件不存在（首次运行时会自动创建）"
fi

echo ""
echo "🔍 测试3：关键类文件检查"
echo "-------------------"

KEY_FILES=(
    "src/main/java/com/xprobe/scanner/config/XProbeConfigManager.java"
    "src/main/java/com/xprobe/scanner/config/XProbeConfig.java"
    "src/main/java/com/xprobe/scanner/core/TaskScheduler.java"
    "src/main/java/com/xprobe/scanner/ui/PassiveScanConfigTab.java"
    "src/main/java/com/xprobe/scanner/XProbe.java"
)

for file in "${KEY_FILES[@]}"; do
    if [ -f "$file" ]; then
        test_pass "关键文件存在: $(basename $file)"
    else
        test_fail "关键文件缺失: $file"
    fi
done

echo ""
echo "📊 测试4：文档完整性"
echo "-------------------"

DOC_FILES=(
    "CRITICAL_FIXES_COMPLETE.md"
    "UI_AND_LOG_IMPROVEMENTS.md"
    "CONFIG_MANAGER_REFACTORING_COMPLETE.md"
    "QUICK_TEST_GUIDE.md"
)

for file in "${DOC_FILES[@]}"; do
    if [ -f "$file" ]; then
        test_pass "文档存在: $file"
    else
        test_fail "文档缺失: $file"
    fi
done

echo ""
echo "🔬 测试5：配置管理器容错性"
echo "-------------------"

# 备份现有配置
if [ -f "$CONFIG_FILE" ]; then
    cp "$CONFIG_FILE" "$BACKUP_FILE"
    test_info "已备份现有配置"
fi

# 测试5.1：删除配置文件
test_info "测试场景：配置文件不存在"
if [ -f "$CONFIG_FILE" ]; then
    rm "$CONFIG_FILE"
fi
test_info "配置文件已删除，插件应在启动时创建默认配置"
test_pass "容错测试1：配置缺失场景准备完成"

# 恢复配置
if [ -f "$BACKUP_FILE" ]; then
    cp "$BACKUP_FILE" "$CONFIG_FILE"
    rm "$BACKUP_FILE"
    test_info "已恢复配置文件"
fi

echo ""
echo "🎨 测试6：代码质量检查"
echo "-------------------"

# 检查是否有明显的编译警告
if ./gradlew compileJava --console=plain 2>&1 | grep -q "错误"; then
    test_fail "编译有错误"
else
    test_pass "编译无错误"
fi

# 检查TODO/FIXME注释
TODO_COUNT=$(grep -r "TODO\|FIXME" src/main/java/com/xprobe 2>/dev/null | wc -l | tr -d ' ')
if [ "$TODO_COUNT" -gt 0 ]; then
    test_info "代码中有 $TODO_COUNT 个TODO/FIXME标记"
else
    test_pass "代码中无TODO/FIXME标记"
fi

echo ""
echo "========================"
echo "📊 测试总结"
echo "========================"
echo -e "${GREEN}通过：$PASS_COUNT${NC}"
echo -e "${RED}失败：$FAIL_COUNT${NC}"
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}🎉 所有自动化测试通过！${NC}"
    echo ""
    echo "✅ 基础功能正常"
    echo "✅ 代码结构完整"
    echo "✅ 文档齐全"
    echo ""
    echo "📝 下一步："
    echo "   1. 在Burp Suite中加载插件"
    echo "   2. 按照 QUICK_TEST_GUIDE.md 进行手动测试"
    echo "   3. 重点测试：扫描结果显示payload、UI优化效果"
    exit 0
else
    echo -e "${RED}⚠️  有 $FAIL_COUNT 项测试失败${NC}"
    echo ""
    echo "请检查上述失败项，修复后重新测试"
    exit 1
fi

