package com.xprobe.scanner.active;

/**
 * 外部工具扫描结果
 */
public class ExternalToolResult {
    private final String url;
    private final String parameter;
    private final String status;
    private final String evidence;
    private final boolean valid;

    public ExternalToolResult(String url, String parameter, String status, String evidence, boolean valid) {
        this.url = url;
        this.parameter = parameter;
        this.status = status;
        this.evidence = evidence;
        this.valid = valid;
    }

    public String getUrl() {
        return url;
    }

    public String getParameter() {
        return parameter;
    }

    public String getStatus() {
        return status;
    }

    public String getEvidence() {
        return evidence;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public String toString() {
        return "ExternalToolResult{" +
                "url='" + url + '\'' +
                ", parameter='" + parameter + '\'' +
                ", status='" + status + '\'' +
                ", valid=" + valid +
                '}';
    }
}
