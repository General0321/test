package com.xprobe.scanner.ui;

import com.xprobe.scanner.active.ParameterDataStorage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 接口/Arjun 探测配置弹窗，负责收集用户选择并交由后端执行。
 */
public class ActiveProbeConfigDialog extends JDialog {

    public enum ProbeMode {
        INTERFACE_ONLY,
        INTERFACE_THEN_ARJUN,
        ARJUN_ONLY
    }

    private final ParameterDataStorage.ParameterDataModel dataModel;
    private final JComboBox<String> presetCombo;
    private final JPanel subdomainTreePanel;
    private final JLabel selectionStatsLabel;
    private final JLabel keywordStatsLabel;
    private final JButton clearSelectionButton;
    private final JRadioButton sourceCollectedRadio;
    private final JRadioButton sourceManualRadio;
    private final JRadioButton sourceAutoRadio;
    private final JTextArea manualEndpointsArea;
    private final JScrollPane manualScrollPane;
    private final JPanel manualInputPanel;
    private final JRadioButton interfaceScopeSubdomain;
    private final JRadioButton interfaceScopeAll;
    private final JRadioButton parameterScopeSubdomain;
    private final JRadioButton parameterScopeAll;
    private final JCheckBox customDictionaryToggle;
    private final JRadioButton strategyInterfaceOnly;
    private final JRadioButton strategyInterfaceThenArjun;
    private final JRadioButton strategyArjunOnly;

    private final ExecutionHandler executionHandler;
    private final Map<String, List<ParameterDataStorage.HostData>> mainDomainHosts = new LinkedHashMap<>();
    private final List<JCheckBox> subdomainCheckboxes = new ArrayList<>();
    private final Map<String, Integer> mainDomainKeywordCounts = new HashMap<>();
    private final Set<String> selectedHostKeys = new HashSet<>();

    public ActiveProbeConfigDialog(Window owner,
                                   ParameterDataStorage.ParameterDataModel dataModel,
                                   ProbeMode mode,
                                   ExecutionHandler executionHandler) {
        super(owner, "接口/Arjun 探测配置", ModalityType.APPLICATION_MODAL);
        this.dataModel = dataModel != null ? dataModel : new ParameterDataStorage.ParameterDataModel();
        this.executionHandler = executionHandler;
        this.presetCombo = new JComboBox<>(new String[]{"默认配置"});
        this.subdomainTreePanel = new JPanel();
        this.selectionStatsLabel = new JLabel("子域：0 接口：0 参数：0");
        this.keywordStatsLabel = new JLabel("关键词：0");
        this.clearSelectionButton = new JButton("清空选择");
        this.sourceCollectedRadio = new JRadioButton("使用已收集数据", true);
        this.sourceManualRadio = new JRadioButton("手动输入");
        this.sourceAutoRadio = new JRadioButton("自动采集（Proxy/SiteMap）");
        this.manualEndpointsArea = new JTextArea(5, 30);
        this.manualScrollPane = new JScrollPane(manualEndpointsArea);
        this.manualInputPanel = new JPanel(new BorderLayout());
        this.interfaceScopeSubdomain = new JRadioButton("仅选中子域接口", true);
        this.interfaceScopeAll = new JRadioButton("主域所有子域接口");
        this.parameterScopeSubdomain = new JRadioButton("仅选中子域参数", true);
        this.parameterScopeAll = new JRadioButton("主域所有子域参数");
        this.customDictionaryToggle = new JCheckBox("启用自定义上传字典");
        this.customDictionaryToggle.setToolTipText("字典在配置中心统一上传，此处仅决定是否启用");
        this.strategyInterfaceOnly = new JRadioButton("仅接口探测");
        this.strategyInterfaceThenArjun = new JRadioButton("接口探测成功后做 Arjun");
        this.strategyArjunOnly = new JRadioButton("直接对接口进行 Arjun（跳过接口探测）");

        buildDomainData();
        initLayout();
        initListeners();
        applyModeDefaults(mode);
        loadInitialData();

        setMinimumSize(new Dimension(760, 600));
        setLocationRelativeTo(owner);
    }

    private void buildDomainData() {
        if (dataModel.mainDomains == null || dataModel.mainDomains.isEmpty()) {
            return;
        }

        dataModel.mainDomains.values()
            .stream()
            .sorted(Comparator.comparing(md -> md.mainDomain == null ? "" : md.mainDomain))
            .forEach(mainDomainData -> {
                List<ParameterDataStorage.HostData> hostDataList = new ArrayList<>();
                if (mainDomainData.hostDataMap != null && !mainDomainData.hostDataMap.isEmpty()) {
                    hostDataList.addAll(mainDomainData.hostDataMap.values());
                } else if (mainDomainData.endpoints != null && !mainDomainData.endpoints.isEmpty()) {
                    ParameterDataStorage.HostData fallbackHost = new ParameterDataStorage.HostData();
                    fallbackHost.host = mainDomainData.mainDomain;
                    fallbackHost.endpoints.addAll(mainDomainData.endpoints);
                    hostDataList.add(fallbackHost);
                }
                if (!hostDataList.isEmpty()) {
                    mainDomainHosts.put(mainDomainData.mainDomain, hostDataList);
                    int keywordCount = mainDomainData.keywords == null ? 0 : mainDomainData.keywords.size();
                    mainDomainKeywordCounts.put(mainDomainData.mainDomain, keywordCount);
                }
            });
    }

    private void initLayout() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(14, 14, 14, 14));
        content.add(buildHeaderPanel(), BorderLayout.NORTH);
        content.add(buildCenterPanel(), BorderLayout.CENTER);
        content.add(buildFooterPanel(), BorderLayout.SOUTH);
        setContentPane(content);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.add(new JLabel("目标配置集:"));
        presetCombo.setPreferredSize(new Dimension(180, 28));
        left.add(presetCombo);
        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton saveDefaultBtn = new JButton("保存");
        saveDefaultBtn.addActionListener(e -> JOptionPane.showMessageDialog(this,
            "此按钮当前仅提供界面效果，后续会接入配置保存逻辑。", "提示", JOptionPane.INFORMATION_MESSAGE));
        JButton saveAsBtn = new JButton("另存为");
        saveAsBtn.addActionListener(e -> JOptionPane.showMessageDialog(this,
            "此按钮当前仅提供界面效果，后续会接入配置保存逻辑。", "提示", JOptionPane.INFORMATION_MESSAGE));
        clearSelectionButton.addActionListener(e -> clearAllSelections());
        right.add(saveDefaultBtn);
        right.add(saveAsBtn);
        right.add(clearSelectionButton);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenterPanel() {
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(buildTargetPanel());
        center.add(Box.createVerticalStrut(10));
        center.add(buildSourcePanel());
        center.add(Box.createVerticalStrut(10));
        center.add(buildScopePanel());
        center.add(Box.createVerticalStrut(10));
        center.add(buildStrategyPanel());
        return center;
    }

    private JPanel buildTargetPanel() {
        JPanel panel = buildTitledPanel("STEP 1 目标选择");
        panel.setLayout(new BorderLayout(8, 8));
        panel.add(buildStatsPanel(), BorderLayout.NORTH);

        subdomainTreePanel.setLayout(new BoxLayout(subdomainTreePanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(subdomainTreePanel);
        scrollPane.setPreferredSize(new Dimension(400, 220));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStatsPanel() {
        JPanel stats = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        selectionStatsLabel.setFont(selectionStatsLabel.getFont().deriveFont(Font.BOLD));
        keywordStatsLabel.setForeground(new Color(80, 120, 200));
        JLabel title = new JLabel("选择统计：");
        stats.add(title);
        stats.add(selectionStatsLabel);
        stats.add(new JLabel(" | "));
        stats.add(keywordStatsLabel);
        return stats;
    }

    private JPanel buildSourcePanel() {
        JPanel panel = buildTitledPanel("STEP 2 接口来源选择（接口数据获取策略）");
        panel.setLayout(new BorderLayout(8, 8));

        JPanel cards = new JPanel(new GridLayout(1, 3, 8, 0));
        ButtonGroup group = new ButtonGroup();
        group.add(sourceCollectedRadio);
        group.add(sourceAutoRadio);
        group.add(sourceManualRadio);
        cards.add(buildSourceCard(sourceCollectedRadio, "复用缓存，速度最快"));
        cards.add(buildSourceCard(sourceAutoRadio, "重新拉取 Proxy/SiteMap"));
        cards.add(buildSourceCard(sourceManualRadio, "直接输入接口路径"));
        panel.add(cards, BorderLayout.NORTH);

        JPanel interfaceScopePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        interfaceScopePanel.add(new JLabel("接口来源范围："));
        ButtonGroup interfaceGroup = new ButtonGroup();
        interfaceGroup.add(interfaceScopeSubdomain);
        interfaceGroup.add(interfaceScopeAll);
        interfaceScopePanel.add(interfaceScopeSubdomain);
        interfaceScopePanel.add(interfaceScopeAll);
        JLabel hint = new JLabel("（影响调度范围，仅在已收集/自动采集模式下可用）");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 12f));
        interfaceScopePanel.add(hint);
        panel.add(interfaceScopePanel, BorderLayout.CENTER);

        manualEndpointsArea.setLineWrap(true);
        manualEndpointsArea.setWrapStyleWord(true);
        manualEndpointsArea.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        manualEndpointsArea.setToolTipText("每行一个接口路径，例如 /api/v1/user/list");
        manualEndpointsArea.setEnabled(false);
        manualScrollPane.setVisible(false);

        manualInputPanel.removeAll();
        manualInputPanel.setBorder(new EmptyBorder(4, 0, 0, 0));
        manualInputPanel.add(manualScrollPane, BorderLayout.CENTER);
        manualInputPanel.setVisible(false);
        panel.add(manualInputPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSourceCard(JRadioButton radioButton, String description) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            new EmptyBorder(8, 8, 8, 8)
        ));
        radioButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(radioButton);
        JLabel desc = new JLabel(description);
        desc.setFont(desc.getFont().deriveFont(Font.PLAIN, 12f));
        desc.setForeground(new Color(90, 90, 90));
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(4));
        card.add(desc);
        return card;
    }

    private JPanel buildScopePanel() {
        JPanel panel = buildTitledPanel("STEP 3 参数范围选择");
        panel.setLayout(new BorderLayout());

        JPanel parameterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        parameterPanel.add(new JLabel("参数范围："));
        ButtonGroup parameterGroup = new ButtonGroup();
        parameterGroup.add(parameterScopeSubdomain);
        parameterGroup.add(parameterScopeAll);
        parameterPanel.add(parameterScopeSubdomain);
        parameterPanel.add(parameterScopeAll);
        parameterPanel.add(customDictionaryToggle);
        panel.add(parameterPanel, BorderLayout.NORTH);

        JTextArea hint = new JTextArea("说明：参数范围独立生效，可与接口范围不同步。");
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setEditable(false);
        hint.setOpaque(false);
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 12f));
        panel.add(hint, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStrategyPanel() {
        JPanel panel = buildTitledPanel("STEP 4 执行策略");
        panel.setLayout(new BorderLayout());
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(4, 8, 4, 8));
        list.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        group.add(strategyInterfaceOnly);
        group.add(strategyInterfaceThenArjun);
        group.add(strategyArjunOnly);
        strategyInterfaceOnly.setSelected(true);
        strategyInterfaceOnly.setAlignmentX(Component.LEFT_ALIGNMENT);
        strategyInterfaceThenArjun.setAlignmentX(Component.LEFT_ALIGNMENT);
        strategyArjunOnly.setAlignmentX(Component.LEFT_ALIGNMENT);

        list.add(strategyInterfaceOnly);
        list.add(Box.createVerticalStrut(4));
        list.add(strategyInterfaceThenArjun);
        list.add(Box.createVerticalStrut(4));
        list.add(strategyArjunOnly);
        panel.add(list, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> dispose());
        JButton executeButton = new JButton("立即执行");
        executeButton.addActionListener(e -> handleExecuteRequest());
        footer.add(cancelButton);
        footer.add(executeButton);
        return footer;
    }

    private JPanel buildTitledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            title
        ));
        return panel;
    }

    private void initListeners() {
        sourceManualRadio.addActionListener(e -> { toggleManualArea(true); updateScopeAvailability(); });
        sourceCollectedRadio.addActionListener(e -> { toggleManualArea(false); updateScopeAvailability(); });
        sourceAutoRadio.addActionListener(e -> { toggleManualArea(false); updateScopeAvailability(); });

        strategyInterfaceOnly.addActionListener(e -> updateScopeAvailability());
        strategyInterfaceThenArjun.addActionListener(e -> updateScopeAvailability());
        strategyArjunOnly.addActionListener(e -> updateScopeAvailability());

        updateScopeAvailability();
    }

    private void toggleManualArea(boolean visible) {
        manualEndpointsArea.setEnabled(visible);
        manualScrollPane.setVisible(visible);
        manualInputPanel.setVisible(visible);
        Container parent = manualScrollPane.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private void updateScopeAvailability() {
        boolean manualSource = sourceManualRadio.isSelected();
        interfaceScopeSubdomain.setEnabled(!manualSource);
        interfaceScopeAll.setEnabled(!manualSource);

        boolean interfaceOnlyStrategy = strategyInterfaceOnly.isSelected();
        parameterScopeSubdomain.setEnabled(!interfaceOnlyStrategy);
        parameterScopeAll.setEnabled(!interfaceOnlyStrategy);
        customDictionaryToggle.setEnabled(!interfaceOnlyStrategy);
    }

    private void loadInitialData() {
        if (mainDomainHosts.isEmpty()) {
            subdomainTreePanel.removeAll();
            subdomainTreePanel.add(new JLabel("未检测到任何主域名，请先收集流量数据。"));
            return;
        }

        refreshHostTree();
    }

    private void refreshHostTree() {
        subdomainTreePanel.removeAll();
        subdomainCheckboxes.clear();

        mainDomainHosts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.nullsLast(String::compareToIgnoreCase)))
            .forEach(entry -> {
                JPanel domainPanel = createDomainSection(entry.getKey(), entry.getValue());
                if (domainPanel != null) {
                    subdomainTreePanel.add(domainPanel);
                    subdomainTreePanel.add(Box.createVerticalStrut(6));
                }
            });

        if (subdomainTreePanel.getComponentCount() == 0) {
            subdomainTreePanel.add(new JLabel("没有可用的子域"));
        }

        subdomainTreePanel.revalidate();
        subdomainTreePanel.repaint();
        updateSelectionStats();
    }

    private JPanel createDomainSection(String mainDomain,
                                       List<ParameterDataStorage.HostData> hosts) {
        JPanel domainPanel = new JPanel(new BorderLayout());
        domainPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(215, 215, 215)),
            new EmptyBorder(4, 8, 4, 8)
        ));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JToggleButton toggle = new JToggleButton("▼", true);
        toggle.setMargin(new Insets(0, 4, 0, 4));
        JLabel title = new JLabel(mainDomain);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        JLabel keywords = new JLabel(String.format("关键词：%d",
            mainDomainKeywordCounts.getOrDefault(mainDomain, 0)));
        keywords.setFont(keywords.getFont().deriveFont(Font.PLAIN, 12f));
        keywords.setForeground(new Color(120, 120, 120));
        header.add(toggle);
        header.add(title);
        header.add(Box.createHorizontalStrut(8));
        header.add(keywords);
        domainPanel.add(header, BorderLayout.NORTH);

        JPanel hostList = new JPanel();
        hostList.setLayout(new BoxLayout(hostList, BoxLayout.Y_AXIS));
        hostList.setBorder(new EmptyBorder(0, 24, 0, 0));
        toggle.addActionListener(e -> hostList.setVisible(toggle.isSelected()));

        boolean hasVisible = false;
        for (ParameterDataStorage.HostData hostData : hosts.stream()
            .sorted(Comparator.comparing(h -> h.host == null ? "" : h.host))
            .collect(Collectors.toList())) {
            String hostName = hostData.host == null ? "(未知子域)" : hostData.host;
            String hostKey = buildHostKey(mainDomain, hostData.host);
            boolean isSelected = selectedHostKeys.contains(hostKey);

            JCheckBox checkBox = new JCheckBox(formatHostLabel(hostName, hostData));
            checkBox.setSelected(isSelected);
            checkBox.putClientProperty("hostKey", hostKey);
            checkBox.putClientProperty("hostName", hostName);
            checkBox.putClientProperty("mainDomain", mainDomain);
            checkBox.addItemListener(event -> {
                if (checkBox.isSelected()) {
                    selectedHostKeys.add(hostKey);
                } else {
                    selectedHostKeys.remove(hostKey);
                }
                updateSelectionStats();
            });
            subdomainCheckboxes.add(checkBox);
            hostList.add(checkBox);
            hasVisible = true;
        }

        if (!hasVisible) {
            return null;
        }

        domainPanel.add(hostList, BorderLayout.CENTER);
        return domainPanel;
    }

    private String formatHostLabel(String hostName, ParameterDataStorage.HostData data) {
        int endpointCount = data.endpoints == null ? 0 : data.endpoints.size();
        int parameterCount = data.parameters == null ? 0 : data.parameters.size();
        return String.format("%s (接口 %d, 参数 %d)", hostName, endpointCount, parameterCount);
    }

    private void updateSelectionStats() {
        int selectedHosts = selectedHostKeys.size();
        int endpointCount = 0;
        int parameterCount = 0;
        int keywordCount = 0;

        for (Map.Entry<String, List<ParameterDataStorage.HostData>> entry : mainDomainHosts.entrySet()) {
            boolean domainSelected = false;
            for (ParameterDataStorage.HostData hostData : entry.getValue()) {
                String key = buildHostKey(entry.getKey(), hostData.host);
                if (!selectedHostKeys.contains(key)) {
                    continue;
                }
                domainSelected = true;
                if (hostData.endpoints != null) {
                    endpointCount += hostData.endpoints.size();
                }
                if (hostData.parameters != null) {
                    parameterCount += hostData.parameters.size();
                }
            }
            if (domainSelected) {
                keywordCount += mainDomainKeywordCounts.getOrDefault(entry.getKey(), 0);
            }
        }

        selectionStatsLabel.setText(String.format("子域：%d   接口：%d   参数：%d", selectedHosts, endpointCount, parameterCount));
        keywordStatsLabel.setText(String.format("关键词：%d", keywordCount));
    }

    private String buildHostKey(String mainDomain, String host) {
        String md = mainDomain == null ? "" : mainDomain;
        String h = host == null ? "(unknown)" : host;
        return md + "||" + h;
    }

    private void clearAllSelections() {
        selectedHostKeys.clear();
        refreshHostTree();
    }

    private void applyModeDefaults(ProbeMode mode) {
        switch (mode) {
            case INTERFACE_ONLY -> strategyInterfaceOnly.setSelected(true);
            case INTERFACE_THEN_ARJUN -> strategyInterfaceThenArjun.setSelected(true);
            case ARJUN_ONLY -> strategyInterfaceThenArjun.setSelected(true);
            default -> strategyInterfaceThenArjun.setSelected(true);
        }
        updateScopeAvailability();
    }

    private void handleExecuteRequest() {
        try {
            if (executionHandler == null) {
                JOptionPane.showMessageDialog(this,
                    "未找到执行器，无法触发探测。",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            ExecutionConfig config = collectExecutionConfig();
            executionHandler.onExecute(config);
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "执行探测失败: " + ex.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private ExecutionConfig collectExecutionConfig() {
        InterfaceSource interfaceSource = sourceManualRadio.isSelected()
            ? InterfaceSource.MANUAL
            : (sourceAutoRadio.isSelected() ? InterfaceSource.AUTO : InterfaceSource.COLLECTED);

        ScopeOption interfaceScope = interfaceScopeAll.isEnabled() && interfaceScopeAll.isSelected()
            ? ScopeOption.ALL_SUBDOMAINS
            : ScopeOption.SELECTED_SUBDOMAINS;

        ScopeOption parameterScope = parameterScopeAll.isEnabled() && parameterScopeAll.isSelected()
            ? ScopeOption.ALL_SUBDOMAINS
            : ScopeOption.SELECTED_SUBDOMAINS;

        ProbeMode strategy = strategyInterfaceOnly.isSelected()
            ? ProbeMode.INTERFACE_ONLY
            : (strategyInterfaceThenArjun.isSelected() ? ProbeMode.INTERFACE_THEN_ARJUN : ProbeMode.ARJUN_ONLY);

        Map<String, Set<String>> selectedHosts = collectSelectedHostsByDomain();
        if (interfaceSource != InterfaceSource.MANUAL && selectedHosts.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个目标子域后再执行。");
        }

        List<String> manualEntries = interfaceSource == InterfaceSource.MANUAL
            ? readManualEndpoints()
            : Collections.emptyList();

        if (interfaceSource == InterfaceSource.MANUAL && manualEntries.isEmpty()) {
            throw new IllegalArgumentException("请在手动输入区域提供至少一个接口路径或URL。");
        }

        return new ExecutionConfig(
            selectedHosts,
            interfaceSource,
            interfaceScope,
            parameterScope,
            strategy,
            customDictionaryToggle.isSelected(),
            manualEntries
        );
    }

    private Map<String, Set<String>> collectSelectedHostsByDomain() {
        Map<String, Set<String>> grouped = new LinkedHashMap<>();
        for (String key : selectedHostKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String[] parts = key.split("\\|\\|", 2);
            String mainDomain = parts.length > 0 ? parts[0] : null;
            String host = parts.length > 1 ? parts[1] : null;
            if (mainDomain == null || mainDomain.isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(mainDomain, md -> new LinkedHashSet<>());
            if (host != null && !host.isBlank() && !"(unknown)".equals(host)) {
                grouped.get(mainDomain).add(host);
            }
        }
        return grouped;
    }

    private List<String> readManualEndpoints() {
        String text = manualEndpointsArea.getText();
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(text.split("\\r?\\n"))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .filter(line -> !line.startsWith("#"))
            .collect(Collectors.toList());
    }

    public enum InterfaceSource {
        COLLECTED,
        AUTO,
        MANUAL
    }

    public enum ScopeOption {
        SELECTED_SUBDOMAINS,
        ALL_SUBDOMAINS
    }

    public static class ExecutionConfig {
        private final Map<String, Set<String>> selectedHostsByDomain;
        private final InterfaceSource interfaceSource;
        private final ScopeOption interfaceScope;
        private final ScopeOption parameterScope;
        private final ProbeMode strategy;
        private final boolean customDictionaryEnabled;
        private final List<String> manualEntries;

        public ExecutionConfig(Map<String, Set<String>> selectedHostsByDomain,
                               InterfaceSource interfaceSource,
                               ScopeOption interfaceScope,
                               ScopeOption parameterScope,
                               ProbeMode strategy,
                               boolean customDictionaryEnabled,
                               List<String> manualEntries) {
            this.selectedHostsByDomain = Collections.unmodifiableMap(new LinkedHashMap<>(selectedHostsByDomain));
            this.interfaceSource = interfaceSource;
            this.interfaceScope = interfaceScope;
            this.parameterScope = parameterScope;
            this.strategy = strategy;
            this.customDictionaryEnabled = customDictionaryEnabled;
            this.manualEntries = Collections.unmodifiableList(new ArrayList<>(manualEntries));
        }

        public Map<String, Set<String>> getSelectedHostsByDomain() {
            return selectedHostsByDomain;
        }

        public InterfaceSource getInterfaceSource() {
            return interfaceSource;
        }

        public ScopeOption getInterfaceScope() {
            return interfaceScope;
        }

        public ScopeOption getParameterScope() {
            return parameterScope;
        }

        public ProbeMode getStrategy() {
            return strategy;
        }

        public boolean isCustomDictionaryEnabled() {
            return customDictionaryEnabled;
        }

        public List<String> getManualEntries() {
            return manualEntries;
        }
    }

    @FunctionalInterface
    public interface ExecutionHandler {
        void onExecute(ExecutionConfig config) throws Exception;
    }
}


