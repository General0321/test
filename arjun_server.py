#!/usr/bin/env python3
"""
Arjun HTTP 服务
绕过 macOS 限制，通过 HTTP API 调用 Arjun

使用方法:
1. 启动服务: python3 arjun_server.py
2. 服务监听: http://localhost:8765
3. Burp 插件通过 HTTP 请求调用 Arjun
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import subprocess
import json
import urllib.parse

class ArjunHandler(BaseHTTPRequestHandler):
    
    def do_POST(self):
        """处理 Arjun 调用请求"""
        try:
            # 读取请求体
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            params = json.loads(post_data.decode('utf-8'))
            
            # 构建 Arjun 命令
            cmd = ['/usr/bin/python3', '/opt/anaconda3/bin/arjun']
            
            # 添加参数
            if 'url' in params:
                cmd.extend(['-u', params['url']])
            if 'method' in params:
                cmd.extend(['-m', params['method']])
            if 'headers' in params:
                cmd.extend(['--headers', params['headers']])
            if 'threads' in params:
                cmd.extend(['-t', str(params['threads'])])
            if 'timeout' in params:
                cmd.extend(['-T', str(params['timeout'])])
            
            # 执行 Arjun
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=300
            )
            
            # 返回结果
            response = {
                'success': result.returncode == 0,
                'stdout': result.stdout,
                'stderr': result.stderr,
                'returncode': result.returncode
            }
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(response).encode('utf-8'))
            
        except Exception as e:
            self.send_response(500)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            error_response = {
                'success': False,
                'error': str(e)
            }
            self.wfile.write(json.dumps(error_response).encode('utf-8'))
    
    def do_GET(self):
        """健康检查"""
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'text/plain')
            self.end_headers()
            self.wfile.write(b'Arjun Server is running')
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        """自定义日志"""
        print(f"[Arjun Server] {format % args}")

if __name__ == '__main__':
    PORT = 8765
    server = HTTPServer(('localhost', PORT), ArjunHandler)
    print(f"🚀 Arjun HTTP Server 启动成功！")
    print(f"📡 监听地址: http://localhost:{PORT}")
    print(f"💡 使用方法:")
    print(f"   POST /  - 调用 Arjun")
    print(f"   GET /health - 健康检查")
    print(f"\n按 Ctrl+C 停止服务\n")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n⏹️  服务已停止")
        server.shutdown()

