package com.xprobe.scanner.ui;

import com.xprobe.scanner.active.ExternalToolConfig;
import com.xprobe.scanner.active.ArjunIntegration;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部工具配置对话框
 */
public class ExternalToolConfigDialog extends JDialog {
    private ExternalToolConfig originalConfig;
    private ExternalToolConfig updatedConfig;
    private boolean configUpdated = false;
    
    // UI组件
    private JTextField arjunPathField;
    private JSpinner threadCountSpinner;
    private JSpinner timeoutSpinner;
    private JTextArea customDictArea;
    private JCheckBox jsonOutputCheckBox;
    private JCheckBox verboseOutputCheckBox;
    
    public ExternalToolConfigDialog(Window parent, ExternalToolConfig config) {
        super(parent, "外部工具配置", Dialog.ModalityType.APPLICATION_MODAL);
        this.originalConfig = config;
        this.updatedConfig = new ExternalToolConfig();
        
        initializeComponents();
        setupLayout();
        setupEventListeners();
        loadConfig();
        
        setSize(500, 400);
        setLocationRelativeTo(parent);
    }
    
    private void initializeComponents() {
        arjunPathField = new JTextField(30);
        threadCountSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 50, 1));
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 300, 5));
        customDictArea = new JTextArea(8, 40);
        customDictArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        jsonOutputCheckBox = new JCheckBox("启用JSON输出");
        verboseOutputCheckBox = new JCheckBox("启用详细输出");
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // 主配置面板
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Arjun路径（带自动检测按钮）
        gbc.gridx = 0; gbc.gridy = 0;
        mainPanel.add(new JLabel("Arjun工具路径:"), gbc);
        
        // 创建路径输入面板（包含输入框和自动检测按钮）
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel pathPanel = new JPanel(new BorderLayout(5, 0));
        pathPanel.add(arjunPathField, BorderLayout.CENTER);
        
        JButton autoDetectButton = new JButton("🔍 自动检测");
        autoDetectButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        autoDetectButton.setToolTipText("自动检测系统中的Arjun路径");
        autoDetectButton.addActionListener(e -> autoDetectArjun());
        pathPanel.add(autoDetectButton, BorderLayout.EAST);
        
        mainPanel.add(pathPanel, gbc);
        
        // 添加路径说明
        gbc.gridx = 1; gbc.gridy = 1;
        JLabel pathHint = new JLabel("<html><small>💡 支持: 完整路径、python3、arjun 等方式</small></html>");
        pathHint.setForeground(Color.GRAY);
        mainPanel.add(pathHint, gbc);
        
        // 线程数
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("线程数:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(threadCountSpinner, gbc);
        
        // 超时时间
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("超时时间(秒):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(timeoutSpinner, gbc);
        
        // 输出选项
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("输出选项:"), gbc);
        gbc.gridx = 1;
        JPanel outputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        outputPanel.add(jsonOutputCheckBox);
        outputPanel.add(verboseOutputCheckBox);
        mainPanel.add(outputPanel, gbc);
        
        // 自定义字典
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("自定义字典:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        JScrollPane dictScrollPane = new JScrollPane(customDictArea);
        dictScrollPane.setBorder(BorderFactory.createTitledBorder("每行一个参数名"));
        mainPanel.add(dictScrollPane, gbc);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("确定");
        JButton cancelButton = new JButton("取消");
        JButton testButton = new JButton("测试连接");
        
        buttonPanel.add(testButton);
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
        
        // 事件监听器
        okButton.addActionListener(e -> saveConfig());
        cancelButton.addActionListener(e -> cancelConfig());
        testButton.addActionListener(e -> testConnection());
    }
    
    private void setupEventListeners() {
        // 可以添加其他事件监听器
    }
    
    private void loadConfig() {
        if (originalConfig != null) {
            arjunPathField.setText(originalConfig.getArjunPath());
            threadCountSpinner.setValue(originalConfig.getThreadCount());
            timeoutSpinner.setValue(originalConfig.getTimeout());
            jsonOutputCheckBox.setSelected(originalConfig.isEnableJsonOutput());
            verboseOutputCheckBox.setSelected(originalConfig.isEnableVerboseOutput());
            
            // 加载自定义字典
            StringBuilder dictText = new StringBuilder();
            for (String param : originalConfig.getCustomDictionary()) {
                dictText.append(param).append("\n");
            }
            customDictArea.setText(dictText.toString());
        }
    }
    
    private void saveConfig() {
        try {
            updatedConfig.setArjunPath(arjunPathField.getText().trim());
            updatedConfig.setThreadCount((Integer) threadCountSpinner.getValue());
            updatedConfig.setTimeout((Integer) timeoutSpinner.getValue());
            updatedConfig.setEnableJsonOutput(jsonOutputCheckBox.isSelected());
            updatedConfig.setEnableVerboseOutput(verboseOutputCheckBox.isSelected());
            
            // 解析自定义字典
            List<String> customDict = new ArrayList<>();
            String[] lines = customDictArea.getText().split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    customDict.add(trimmed);
                }
            }
            updatedConfig.setCustomDictionary(customDict);
            
            configUpdated = true;
            dispose();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "配置保存失败: " + e.getMessage(), 
                "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void cancelConfig() {
        configUpdated = false;
        dispose();
    }
    
    /**
     * ✅ 自动检测Arjun路径
     */
    private void autoDetectArjun() {
        // 显示检测中提示
        JDialog progressDialog = new JDialog(this, "自动检测", true);
        progressDialog.setLayout(new BorderLayout(10, 10));
        progressDialog.add(new JLabel("  🔍 正在搜索系统中的Arjun...  "), BorderLayout.NORTH);
        
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressDialog.add(progressBar, BorderLayout.CENTER);
        
        progressDialog.setSize(300, 100);
        progressDialog.setLocationRelativeTo(this);
        
        // 异步执行检测
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return ArjunIntegration.autoDetectArjunPath();
            }
            
            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    String detectedPath = get();
                    if (detectedPath != null && !detectedPath.isEmpty()) {
                        arjunPathField.setText(detectedPath);
                        
                        String osName = System.getProperty("os.name");
                        String message = String.format(
                            "✅ 自动检测成功！\n\n" +
                            "检测到的路径: %s\n\n" +
                            "系统: %s\n" +
                            "提示: 如果需要更改，可以手动输入其他路径。",
                            detectedPath,
                            osName
                        );
                        
                        JOptionPane.showMessageDialog(
                            ExternalToolConfigDialog.this,
                            message,
                            "检测成功",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        showAutoDetectFailedDialog();
                    }
                } catch (Exception e) {
                    showAutoDetectFailedDialog();
                }
            }
        };
        
        worker.execute();
        progressDialog.setVisible(true);
    }
    
    /**
     * ✅ 显示自动检测失败对话框
     */
    private void showAutoDetectFailedDialog() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        
        StringBuilder message = new StringBuilder();
        message.append("❌ 未能自动检测到Arjun\n\n");
        message.append("请手动配置Arjun路径，支持以下方式：\n\n");
        
        if (isWindows) {
            message.append("1️⃣ 填写 python 或 python3（推荐）\n");
            message.append("   会自动使用: python -m arjun\n\n");
            message.append("2️⃣ 填写完整路径，例如：\n");
            message.append("   C:\\Python39\\Scripts\\arjun.exe\n");
            message.append("   C:\\Users\\YourName\\anaconda3\\Scripts\\arjun.exe\n\n");
            message.append("3️⃣ 如果Arjun未安装：\n");
            message.append("   pip install arjun\n");
        } else {
            message.append("1️⃣ 填写 python3（推荐）\n");
            message.append("   会自动使用: python3 -m arjun\n\n");
            message.append("2️⃣ 填写完整路径，例如：\n");
            message.append("   /usr/local/bin/arjun\n");
            message.append("   /opt/anaconda3/bin/arjun\n");
            message.append("   /home/user/anaconda3/bin/arjun\n\n");
            message.append("3️⃣ 如果Arjun未安装：\n");
            message.append("   pip3 install arjun\n");
        }
        
        JOptionPane.showMessageDialog(
            this,
            message.toString(),
            "检测失败",
            JOptionPane.WARNING_MESSAGE
        );
    }
    
    private void testConnection() {
        String arjunPath = arjunPathField.getText().trim();
        if (arjunPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入Arjun工具路径", 
                "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder(arjunPath, "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                JOptionPane.showMessageDialog(this, "Arjun工具连接成功！", 
                    "测试结果", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Arjun工具连接失败，退出码: " + exitCode, 
                    "测试结果", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "测试连接时出错: " + e.getMessage(), 
                "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public boolean isConfigUpdated() {
        return configUpdated;
    }
    
    public ExternalToolConfig getUpdatedConfig() {
        return updatedConfig;
    }
}
