package com.xprobe.scanner.ui;

import com.xprobe.scanner.active.ParameterDataStorage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 仅负责探测配置弹窗的UI表现，功能逻辑后续接入。
 */
public class ActiveProbeConfigDialog extends JDialog {

    public enum ProbeMode {
        INTERFACE_ONLY,
        INTERFACE_THEN_ARJUN,
        ARJUN_ONLY
    }

    private final ParameterDataStorage.ParameterDataModel dataModel;
    private final JComboBox<String> presetCombo;
    private final JComboBox<String> mainDomainCombo;
    private final JTextField searchField;
    private final JPanel subdomainListPanel;
    private final JLabel previewLabel;
    private final JRadioButton sourceCollectedRadio;
    private final JRadioButton sourceManualRadio;
    private final JRadioButton sourceAutoRadio;
    private final JTextArea manualEndpointsArea;
    private final JScrollPane manualScrollPane;
    private final JRadioButton interfaceScopeSubdomain;
    private final JRadioButton interfaceScopeAll;
    private final JRadioButton parameterScopeSubdomain;
    private final JRadioButton parameterScopeAll;
    private final JRadioButton strategyInterfaceOnly;
    private final JRadioButton strategyInterfaceThenArjun;
    private final JRadioButton strategyArjunOnly;

    private final Map<String, List<ParameterDataStorage.HostData>> mainDomainHosts = new LinkedHashMap<>();
    private final List<JCheckBox> subdomainCheckboxes = new ArrayList<>();

    public ActiveProbeConfigDialog(Window owner,
                                   ParameterDataStorage.ParameterDataModel dataModel,
                                   ProbeMode mode) {
        super(owner, "接口/Arjun 探测配置", ModalityType.APPLICATION_MODAL);
        this.dataModel = dataModel != null ? dataModel : new ParameterDataStorage.ParameterDataModel();
        this.presetCombo = new JComboBox<>(new String[]{"默认配置"});
        this.mainDomainCombo = new JComboBox<>();
        this.searchField = new JTextField();
        this.subdomainListPanel = new JPanel();
        this.previewLabel = new JLabel("已选 0 个子域 | 接口 0 | 参数 0");
        this.sourceCollectedRadio = new JRadioButton("使用已收集数据", true);
        this.sourceManualRadio = new JRadioButton("手动输入");
        this.sourceAutoRadio = new JRadioButton("自动采集（Proxy/SiteMap）");
        this.manualEndpointsArea = new JTextArea(5, 30);
        this.manualScrollPane = new JScrollPane(manualEndpointsArea);
        this.interfaceScopeSubdomain = new JRadioButton("仅选中子域接口", true);
        this.interfaceScopeAll = new JRadioButton("主域所有子域接口");
        this.parameterScopeSubdomain = new JRadioButton("仅选中子域参数", true);
        this.parameterScopeAll = new JRadioButton("主域所有子域参数");
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
        left.add(new JLabel("配置:"));
        presetCombo.setPreferredSize(new Dimension(180, 28));
        left.add(presetCombo);
        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton saveDefaultBtn = new JButton("保存为默认");
        saveDefaultBtn.addActionListener(e -> JOptionPane.showMessageDialog(this,
            "此按钮当前仅提供界面效果，后续会接入配置保存逻辑。", "提示", JOptionPane.INFORMATION_MESSAGE));
        JButton saveAsBtn = new JButton("另存为");
        saveAsBtn.addActionListener(e -> JOptionPane.showMessageDialog(this,
            "此按钮当前仅提供界面效果，后续会接入配置保存逻辑。", "提示", JOptionPane.INFORMATION_MESSAGE));
        right.add(saveDefaultBtn);
        right.add(saveAsBtn);
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
        JPanel panel = buildTitledPanel("目标选择");
        panel.setLayout(new BorderLayout(8, 8));

        JPanel selectionPanel = new JPanel(new BorderLayout(6, 6));
        JPanel mainDomainRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        mainDomainRow.add(new JLabel("选择主域:"));
        mainDomainCombo.setPreferredSize(new Dimension(200, 28));
        mainDomainRow.add(mainDomainCombo);
        mainDomainRow.add(new JLabel("搜索子域:"));
        searchField.setPreferredSize(new Dimension(200, 28));
        mainDomainRow.add(searchField);
        selectionPanel.add(mainDomainRow, BorderLayout.NORTH);

        subdomainListPanel.setLayout(new BoxLayout(subdomainListPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(subdomainListPanel);
        scrollPane.setPreferredSize(new Dimension(300, 150));
        selectionPanel.add(scrollPane, BorderLayout.CENTER);
        selectionPanel.add(previewLabel, BorderLayout.SOUTH);

        panel.add(selectionPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSourcePanel() {
        JPanel panel = buildTitledPanel("接口来源");
        panel.setLayout(new BorderLayout());

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));

        JPanel radios = new JPanel();
        radios.setLayout(new BoxLayout(radios, BoxLayout.Y_AXIS));
        radios.setBorder(new EmptyBorder(0, 0, 0, 8));
        ButtonGroup group = new ButtonGroup();
        group.add(sourceCollectedRadio);
        group.add(sourceManualRadio);
        group.add(sourceAutoRadio);
        radios.add(sourceCollectedRadio);
        radios.add(Box.createVerticalStrut(4));
        radios.add(sourceManualRadio);
        radios.add(Box.createVerticalStrut(4));
        radios.add(sourceAutoRadio);

        manualEndpointsArea.setLineWrap(true);
        manualEndpointsArea.setWrapStyleWord(true);
        manualEndpointsArea.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        manualEndpointsArea.setToolTipText("每行一个接口路径，例如 /api/v1/user/list");
        manualEndpointsArea.setEnabled(false);
        manualScrollPane.setPreferredSize(new Dimension(260, 120));
        manualScrollPane.setMaximumSize(new Dimension(260, Integer.MAX_VALUE));
        manualScrollPane.setVisible(false);

        container.add(radios);
        container.add(Box.createHorizontalStrut(12));
        container.add(manualScrollPane);
        container.add(Box.createHorizontalGlue());
        panel.add(container, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildScopePanel() {
        JPanel panel = buildTitledPanel("接口与参数范围");
        panel.setLayout(new GridLayout(2, 1, 4, 4));

        JPanel interfacePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        interfacePanel.add(new JLabel("接口范围:"));
        ButtonGroup interfaceGroup = new ButtonGroup();
        interfaceGroup.add(interfaceScopeSubdomain);
        interfaceGroup.add(interfaceScopeAll);
        interfacePanel.add(interfaceScopeSubdomain);
        interfacePanel.add(interfaceScopeAll);

        JPanel parameterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        parameterPanel.add(new JLabel("参数范围:"));
        ButtonGroup parameterGroup = new ButtonGroup();
        parameterGroup.add(parameterScopeSubdomain);
        parameterGroup.add(parameterScopeAll);
        parameterPanel.add(parameterScopeSubdomain);
        parameterPanel.add(parameterScopeAll);

        panel.add(interfacePanel);
        panel.add(parameterPanel);
        return panel;
    }

    private JPanel buildStrategyPanel() {
        JPanel panel = buildTitledPanel("执行策略");
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
        panel.add(list, BorderLayout.WEST);
        return panel;
    }

    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> dispose());
        JButton executeButton = new JButton("立即执行");
        executeButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                "当前仅实现界面展示，执行逻辑将在后续版本接入。",
                "提示",
                JOptionPane.INFORMATION_MESSAGE);
            dispose();
        });
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

        mainDomainCombo.addActionListener(e -> refreshSubdomainList());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { refreshSubdomainList(); }
            @Override
            public void removeUpdate(DocumentEvent e) { refreshSubdomainList(); }
            @Override
            public void changedUpdate(DocumentEvent e) { refreshSubdomainList(); }
        });

        strategyInterfaceOnly.addActionListener(e -> updateScopeAvailability());
        strategyInterfaceThenArjun.addActionListener(e -> updateScopeAvailability());
        strategyArjunOnly.addActionListener(e -> updateScopeAvailability());

        updateScopeAvailability();
    }

    private void toggleManualArea(boolean visible) {
        manualEndpointsArea.setEnabled(visible);
        manualScrollPane.setVisible(visible);
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

        boolean interfaceOnly = strategyInterfaceOnly.isSelected();
        parameterScopeSubdomain.setEnabled(!interfaceOnly);
        parameterScopeAll.setEnabled(!interfaceOnly);
    }

    private void loadInitialData() {
        if (mainDomainHosts.isEmpty()) {
            mainDomainCombo.addItem("暂无数据");
            mainDomainCombo.setEnabled(false);
            searchField.setEnabled(false);
            subdomainListPanel.removeAll();
            subdomainListPanel.add(new JLabel("未检测到任何主域名，请先收集流量数据。"));
            return;
        }

        mainDomainHosts.keySet().forEach(mainDomainCombo::addItem);
        mainDomainCombo.setSelectedIndex(0);
        refreshSubdomainList();
    }

    private void refreshSubdomainList() {
        subdomainListPanel.removeAll();
        subdomainCheckboxes.clear();
        String selectedDomain = (String) mainDomainCombo.getSelectedItem();
        if (selectedDomain == null || !mainDomainHosts.containsKey(selectedDomain)) {
            subdomainListPanel.add(new JLabel("请选择主域名"));
            previewLabel.setText("已选 0 个子域 | 接口 0 | 参数 0");
            revalidate();
            repaint();
            return;
        }

        List<ParameterDataStorage.HostData> hostData = mainDomainHosts.get(selectedDomain);
        String filter = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);

        hostData.stream()
            .sorted(Comparator.comparing(h -> h.host == null ? "" : h.host))
            .filter(h -> filter.isEmpty() || (h.host != null && h.host.toLowerCase(Locale.ROOT).contains(filter)))
            .forEach(host -> {
                String label = host.host == null ? "(未知子域)" : host.host;
                JCheckBox checkBox = new JCheckBox(label, false);
                checkBox.addItemListener(event -> updatePreview());
                subdomainCheckboxes.add(checkBox);
                subdomainListPanel.add(checkBox);
            });

        if (subdomainCheckboxes.isEmpty()) {
            subdomainListPanel.add(new JLabel("没有匹配的子域"));
        }
        updatePreview();
        subdomainListPanel.revalidate();
        subdomainListPanel.repaint();
    }

    private void updatePreview() {
        String selectedDomain = (String) mainDomainCombo.getSelectedItem();
        if (selectedDomain == null || !mainDomainHosts.containsKey(selectedDomain)) {
            previewLabel.setText("已选 0 个子域 | 接口 0 | 参数 0");
            return;
        }

        List<String> selectedHosts = subdomainCheckboxes.stream()
            .filter(AbstractButton::isSelected)
            .map(AbstractButton::getText)
            .collect(Collectors.toList());

        List<ParameterDataStorage.HostData> hostData = mainDomainHosts.get(selectedDomain);
        int endpointCount = 0;
        int parameterCount = 0;

        for (ParameterDataStorage.HostData host : hostData) {
            if (!selectedHosts.contains(host.host)) {
                continue;
            }
            if (host.endpoints != null) {
                endpointCount += host.endpoints.size();
            }
            if (host.parameters != null) {
                parameterCount += host.parameters.size();
            }
        }

        previewLabel.setText(String.format(
            "已选 %d 个子域 | 接口 %d | 参数 %d",
            selectedHosts.size(),
            endpointCount,
            parameterCount
        ));
    }

    private void applyModeDefaults(ProbeMode mode) {
        switch (mode) {
            case INTERFACE_ONLY -> strategyInterfaceOnly.setSelected(true);
            case INTERFACE_THEN_ARJUN -> strategyInterfaceThenArjun.setSelected(true);
            case ARJUN_ONLY -> strategyInterfaceThenArjun.setSelected(true);
            default -> strategyInterfaceOnly.setSelected(true);
        }
    }
}


