# Arjun HTTP 服务方案

## 🎯 适用场景

当遇到以下情况时，使用此方案：

1. **macOS 安全限制**：`posix_spawn failed` 错误
2. **沙箱环境**：Java 无法直接启动外部进程
3. **容器环境**：Docker/K8s 等隔离环境
4. **跨平台兼容**：统一的调用方式

---

## 📦 方案架构

```
┌─────────────┐     HTTP      ┌──────────────┐     subprocess    ┌────────┐
│  Burp 插件  │ ────────────> │ Python HTTP  │ ────────────────> │ Arjun  │
│  (Java)     │               │   Server     │                   │        │
└─────────────┘     请求/响应  └──────────────┘     标准输入/输出  └────────┘

优势：
├─ 绕过 Java ProcessBuilder 限制
├─ 支持所有平台（macOS/Windows/Linux）
├─ 统一的调用接口
└─ 独立运行，不受 Burp 影响
```

---

## 🚀 快速开始

### 步骤1: 启动 HTTP 服务

```bash
# 方法1: 直接启动（前台）
cd /Users/0x7llcf/Desktop/tools/burp插件/XProbe
python3 arjun_server.py

# 方法2: 后台运行
nohup python3 arjun_server.py > /tmp/arjun-service.log 2>&1 &

# 方法3: 使用 screen（推荐）
screen -S arjun
python3 arjun_server.py
# 按 Ctrl+A, D 退出
```

### 步骤2: 验证服务

```bash
# 健康检查
curl http://localhost:8765/health

# 预期输出
Arjun Server is running
```

### 步骤3: 配置插件

1. 打开 Burp Suite
2. 加载 XProbe 插件
3. 在"配置"选项卡中：
   - **Arjun 路径**：保持原配置（如 `/opt/anaconda3/bin/arjun`）
   - 点击"测试连接"
4. 插件会自动检测服务并使用 HTTP 方式调用

---

## 🔧 服务管理

### 查看服务状态

```bash
# 方法1: 检查端口
lsof -i :8765

# 方法2: 检查进程
ps aux | grep arjun_server

# 方法3: 健康检查
curl http://localhost:8765/health
```

### 停止服务

```bash
# 方法1: 找到进程并停止
lsof -ti:8765 | xargs kill

# 方法2: 根据进程名停止
pkill -f arjun_server.py

# 方法3: 优雅停止（如果在 screen 中）
screen -r arjun
# 按 Ctrl+C
```

### 查看日志

```bash
# 如果使用 nohup
tail -f /tmp/arjun-service.log

# 如果使用 screen
screen -r arjun
```

---

## 📋 API 接口

### POST /

**功能**：执行 Arjun 扫描

**请求格式**：
```json
{
  "url": "http://example.com/api/endpoint",
  "method": "GET",
  "headers": "Cookie: session=abc123\nUser-Agent: Mozilla/5.0",
  "proxy_url": "http://127.0.0.1:8080",
  "arjun_path": "/opt/anaconda3/bin/arjun",
  "python_path": "/usr/bin/python3"
}
```

**响应格式**：
```json
{
  "success": true,
  "stdout": "[ARJUN OUTPUT]",
  "stderr": "",
  "returncode": 0
}
```

### GET /health

**功能**：健康检查

**响应**：
```
Arjun Server is running
```

---

## 🔍 故障排查

### 问题1: 服务无法启动

```bash
# 检查端口是否被占用
lsof -i :8765

# 如果被占用，杀掉进程
kill $(lsof -ti:8765)

# 或者修改端口
vim arjun_server.py
# 修改最后一行的端口号
```

### 问题2: Arjun 调用失败

```bash
# 查看服务日志
tail -f /tmp/arjun-service.log

# 手动测试 Arjun
python3 /opt/anaconda3/bin/arjun --help

# 检查 Arjun 路径
ls -la /opt/anaconda3/bin/arjun
```

### 问题3: 插件无法连接服务

```bash
# 1. 确认服务在运行
curl http://localhost:8765/health

# 2. 检查防火墙
# macOS
sudo /usr/libexec/ApplicationFirewall/socketfilterfw --getglobalstate

# 3. 查看 Burp 日志
# 在 Burp 的 "扩展 -> XProbe" 查看调试信息
```

---

## 💡 性能优化

### 1. 使用进程池

修改 `arjun_server.py`：

```python
from concurrent.futures import ProcessPoolExecutor

executor = ProcessPoolExecutor(max_workers=4)

class ArjunHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        # ... 解析请求 ...
        
        # 使用进程池执行
        future = executor.submit(subprocess.run, cmd, ...)
        result = future.result()
        
        # ... 返回响应 ...
```

### 2. 添加缓存

```python
from functools import lru_cache

@lru_cache(maxsize=100)
def run_arjun(url, method):
    # ... Arjun 调用 ...
```

### 3. 限流

```python
from time import time

last_request = {}

def rate_limit(url):
    now = time()
    if url in last_request and now - last_request[url] < 1:
        return False
    last_request[url] = now
    return True
```

---

## 🔒 安全建议

### 1. 仅监听本地

```python
# arjun_server.py 最后一行
server_address = ('127.0.0.1', 8765)  # 仅本地访问
```

### 2. 添加认证

```python
class ArjunHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        auth = self.headers.get('Authorization')
        if auth != 'Bearer YOUR_SECRET_TOKEN':
            self.send_response(403)
            return
        # ... 继续处理 ...
```

### 3. 限制请求来源

```python
class ArjunHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        client_ip = self.client_address[0]
        if client_ip != '127.0.0.1':
            self.send_response(403)
            return
        # ... 继续处理 ...
```

---

## 🌐 跨平台部署

### Windows

```batch
REM 启动服务
start /B python arjun_server.py > arjun-service.log 2>&1

REM 停止服务
taskkill /F /IM python.exe /FI "WINDOWTITLE eq arjun_server.py"
```

### Linux

```bash
# 使用 systemd
sudo nano /etc/systemd/system/arjun.service

# 内容：
[Unit]
Description=Arjun HTTP Service
After=network.target

[Service]
Type=simple
User=youruser
WorkingDirectory=/path/to/XProbe
ExecStart=/usr/bin/python3 arjun_server.py
Restart=always

[Install]
WantedBy=multi-user.target

# 启动
sudo systemctl start arjun
sudo systemctl enable arjun
```

### Docker

```dockerfile
FROM python:3.11-slim

RUN pip install arjun-scanner

COPY arjun_server.py /app/
WORKDIR /app

EXPOSE 8765

CMD ["python3", "arjun_server.py"]
```

```bash
# 构建
docker build -t arjun-service .

# 运行
docker run -d -p 8765:8765 arjun-service
```

---

## 📝 总结

| 方面 | 传统方式 | HTTP 服务方式 |
|------|---------|---------------|
| **macOS 兼容性** | ❌ `posix_spawn failed` | ✅ 完美运行 |
| **跨平台** | ⚠️ 依赖系统环境 | ✅ 统一接口 |
| **性能** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ (略慢1ms) |
| **管理复杂度** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ (需单独启动) |
| **调试** | ⚠️ 困难 | ✅ 独立日志 |
| **隔离性** | ⚠️ 进程耦合 | ✅ 完全隔离 |

---

## 🔗 相关文档

- [Arjun 使用指南](ARJUN_INTEGRATION_GUIDE.md)
- [Arjun 命令示例](ARJUN_COMMAND_EXAMPLES.md)
- [README](README.md)

