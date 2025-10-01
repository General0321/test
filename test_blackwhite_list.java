// 测试黑白名单逻辑
public class TestBlackWhiteList {
    
    public static void main(String[] args) {
        // 模拟您的配置
        String[] whitelist = {"shengx.com"};
        String[] blacklist = {"static.shengx.com"};
        
        // 测试URL
        String[] testUrls = {
            "https://shengx.com/api/users",
            "https://static.shengx.com/css/style.css", 
            "https://other.com/api",
            "https://shengx.com/admin/login",
            "https://static.shengx.com/js/app.js"
        };
        
        System.out.println("=== 黑白名单测试 ===");
        System.out.println("白名单: shengx.com");
        System.out.println("黑名单: static.shengx.com");
        System.out.println();
        
        for (String url : testUrls) {
            boolean shouldScan = shouldScan(url, whitelist, blacklist);
            System.out.println("URL: " + url);
            System.out.println("结果: " + (shouldScan ? "✅ 扫描" : "❌ 不扫描"));
            System.out.println();
        }
    }
    
    private static boolean shouldScan(String url, String[] whitelist, String[] blacklist) {
        // 白名单检查
        boolean inWhitelist = false;
        for (String item : whitelist) {
            if (url.contains(item)) {
                inWhitelist = true;
                break;
            }
        }
        if (!inWhitelist) {
            return false; // 不在白名单中
        }
        
        // 黑名单检查
        for (String item : blacklist) {
            if (url.contains(item)) {
                return false; // 在黑名单中
            }
        }
        
        return true; // 通过所有检查
    }
}
