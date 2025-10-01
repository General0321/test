package com.xprobe.scanner.active;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 主动扫描目标
 */
public class ScanTarget {
    private final String url;
    private final LocalDateTime createdTime;
    private ScanStatus status;
    private int discoveredEndpoints;
    private int discoveredParameters;

    public ScanTarget(String url) {
        this.url = url;
        this.createdTime = LocalDateTime.now();
        this.status = ScanStatus.PENDING;
        this.discoveredEndpoints = 0;
        this.discoveredParameters = 0;
    }

    public String getUrl() {
        return url;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
    }

    public int getDiscoveredEndpoints() {
        return discoveredEndpoints;
    }

    public void setDiscoveredEndpoints(int discoveredEndpoints) {
        this.discoveredEndpoints = discoveredEndpoints;
    }

    public int getDiscoveredParameters() {
        return discoveredParameters;
    }

    public void setDiscoveredParameters(int discoveredParameters) {
        this.discoveredParameters = discoveredParameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScanTarget that = (ScanTarget) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return "ScanTarget{" +
                "url='" + url + '\'' +
                ", status=" + status +
                ", discoveredEndpoints=" + discoveredEndpoints +
                ", discoveredParameters=" + discoveredParameters +
                '}';
    }

    public enum ScanStatus {
        PENDING("等待中"),
        SCANNING("扫描中"),
        COMPLETED("完成"),
        ERROR("错误"),
        CANCELLED("已取消");

        private final String displayName;

        ScanStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
