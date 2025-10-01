package com.xprobe.scanner.active;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 主动扫描结果
 */
public class ScanResult {
    private final ScanTarget target;
    private final String endpoint;
    private final String parameter;
    private final String type;
    private final String status;
    private final LocalDateTime timestamp;
    private final String evidence;

    public ScanResult(ScanTarget target, String endpoint, String parameter, String type, String status, String evidence) {
        this.target = target;
        this.endpoint = endpoint;
        this.parameter = parameter;
        this.type = type;
        this.status = status;
        this.timestamp = LocalDateTime.now();
        this.evidence = evidence;
    }

    public ScanTarget getTarget() {
        return target;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getParameter() {
        return parameter;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getEvidence() {
        return evidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScanResult that = (ScanResult) o;
        return Objects.equals(target, that.target) &&
                Objects.equals(endpoint, that.endpoint) &&
                Objects.equals(parameter, that.parameter) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, endpoint, parameter, type);
    }

    @Override
    public String toString() {
        return "ScanResult{" +
                "target=" + target.getUrl() +
                ", endpoint='" + endpoint + '\'' +
                ", parameter='" + parameter + '\'' +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
