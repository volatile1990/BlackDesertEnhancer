package enhancer.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import enhancer.calculator.AccessoryProfitCalculator;
import enhancer.market.BDOMarket;
import enhancer.models.AccessoryResult;
import enhancer.models.AccessoryStack;
import enhancer.models.market.Accessory;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class EnhanceProfitGUI extends JFrame {
    private JTable mainTable;
    private final AccessoryProfitCalculator calculator;
    private List<AccessoryResult> results;
    private final JLabel statusLabel;
    private JSpinner simulationRunsSpinner;
    private JTextField filterTextField;
    private TableRowSorter<TableModel> tableRowSorter;

    // Die beiden Buttons
    private JButton loadMarketDataButton;
    private JButton calculateButton;
    private JButton optimizeButton;
    private JButton optimizeSelectedButton;

    // Liste der geladenen Accessoires
    private List<Accessory> marketAccessories;

    // Stack selection combo boxes
    private JComboBox<AccessoryStack> monStackCombo;
    private JComboBox<AccessoryStack> duoStackCombo;
    private JComboBox<AccessoryStack> triStackCombo;
    private JComboBox<AccessoryStack> tetStackCombo;

    public EnhanceProfitGUI() {
        super("BDO Accessory Enhancement Analyzer");
        this.calculator = new AccessoryProfitCalculator();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);

        // Setup FlatLaf dark theme
        setupFlatLafDarkTheme();

        // Main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Create top control panel
        JPanel controlPanel = createControlPanel();

        // Create stack selection panel
        JPanel stackPanel = createStackSelectionPanel();

        // Create filter panel
        JPanel filterPanel = createFilterPanel();

        // Combine control panels
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(controlPanel, BorderLayout.NORTH);
        topPanel.add(stackPanel, BorderLayout.CENTER);
        topPanel.add(filterPanel, BorderLayout.SOUTH);

        // Bottom status panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Ready");
        statusPanel.add(statusLabel, BorderLayout.WEST);

        // Create the main table
        createMainTable();
        JScrollPane tableScrollPane = new JScrollPane(mainTable);

        // Add all components to main panel
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(tableScrollPane, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        setLocationRelativeTo(null);

        // Initial button state
        calculateButton.setEnabled(false); // Deaktivieren bis Daten geladen sind
        optimizeButton.setEnabled(false);
    }

    private void setupFlatLafDarkTheme() {
        try {
            // Setup FlatLaf Dark theme
            FlatDarkLaf.setup();
            // You can customize specific FlatLaf properties if needed
            UIManager.put("Button.arc", 5);
            UIManager.put("Component.arc", 5);
            UIManager.put("ProgressBar.arc", 5);
            UIManager.put("Table.showVerticalLines", true);
            UIManager.put("Table.showHorizontalLines", true);
        } catch (Exception e) {
            log.error("Failed to initialize FlatLaf dark theme", e);
            // Fall back to system look and feel if FlatLaf fails
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                log.error("Failed to set system look and feel as fallback", ex);
            }
        }
    }

    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

        // Buttons erstellen
        loadMarketDataButton = new JButton("Fetch & Calculate");
        loadMarketDataButton.addActionListener(e -> loadMarketData());

        calculateButton = new JButton("Calculate");
        calculateButton.addActionListener(e -> calculateProfits());

        // Button für Stack-Optimierung
        optimizeButton = new JButton("Optimize Stacks");
        optimizeButton.setToolTipText("Find optimal failstack combinations for each accessory");
        optimizeButton.addActionListener(e -> {
            if (marketAccessories == null || marketAccessories.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please load market data first!",
                        "No Data",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Rufe die OptimalStackDialog-Klasse auf
            OptimalStackDialog.optimizeAndShowDialog(this, marketAccessories, this);
        });

        // Button for optimizing selected accessories
        JButton optimizeSelectedButton = getOptimizeSelectedButton();

        // Simulation Runs Konfiguration hinzufügen
        JLabel simulationRunsLabel = new JLabel("Simulation Runs:");
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(
                calculator.getSimulationRuns(), // initial value
                1000,                           // minimum value
                1000000,                        // maximum value
                10000);                         // step size
        simulationRunsSpinner = new JSpinner(spinnerModel);
        simulationRunsSpinner.setEditor(new JSpinner.NumberEditor(simulationRunsSpinner, "#,###"));

        // Dimension festlegen für bessere Darstellung
        Dimension spinnerSize = new Dimension(120, calculateButton.getPreferredSize().height);
        simulationRunsSpinner.setPreferredSize(spinnerSize);

        controlPanel.add(loadMarketDataButton);
        controlPanel.add(calculateButton);
        controlPanel.add(optimizeButton);
        controlPanel.add(optimizeSelectedButton);
        controlPanel.add(Box.createHorizontalStrut(20)); // Abstand zwischen Elementen
        controlPanel.add(simulationRunsLabel);
        controlPanel.add(simulationRunsSpinner);

        return controlPanel;
    }

    private JButton getOptimizeSelectedButton() {
        optimizeSelectedButton = new JButton("Optimize Selected");
        optimizeSelectedButton.setToolTipText("Find optimal failstack combinations for selected accessories only");
        optimizeSelectedButton.setEnabled(false); // Initially disabled until selection happens

        optimizeSelectedButton.addActionListener(e -> {
            // Check if market data is loaded
            if (marketAccessories == null || marketAccessories.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please load market data first!",
                        "No Data",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Get selected rows from the table
            int[] selectedRows = mainTable.getSelectedRows();
            if (selectedRows.length == 0) {
                JOptionPane.showMessageDialog(this,
                        "Please select at least one accessory from the table",
                        "No Selection",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Convert view indices to model indices (important when table is sorted/filtered)
            int[] modelIndices = new int[selectedRows.length];
            for (int i = 0; i < selectedRows.length; i++) {
                modelIndices[i] = mainTable.convertRowIndexToModel(selectedRows[i]);
            }

            // Get accessory names from the selected rows
            List<String> selectedAccessoryNames = new ArrayList<>();
            for (int modelIndex : modelIndices) {
                String accessoryName = (String) mainTable.getModel().getValueAt(modelIndex, 0);
                selectedAccessoryNames.add(accessoryName);
            }

            // Filter market accessories to only those selected
            List<Accessory> selectedAccessories = marketAccessories.stream()
                    .filter(accessory -> selectedAccessoryNames.contains(accessory.getName()))
                    .collect(Collectors.toList());

            // Run optimization on selected accessories
            optimizeSelectedAccessories(selectedAccessories);
        });

        return optimizeSelectedButton;
    }

    private JPanel createFilterPanel() {
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Filter"));

        JLabel filterLabel = new JLabel("Filter by Name:");
        filterTextField = new JTextField(20);

        // Add document listener to implement live filtering
        filterTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateFilter();
            }
        });

        filterPanel.add(filterLabel);
        filterPanel.add(filterTextField);

        // Add clear button
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            filterTextField.setText("");
            updateFilter();
        });
        filterPanel.add(clearButton);

        return filterPanel;
    }

    private void updateFilter() {
        String text = filterTextField.getText();
        if (text.trim().isEmpty()) {
            tableRowSorter.setRowFilter(null);
            statusLabel.setText("Filter cleared");
        } else {
            try {
                // Case insensitive filter on the Name column (index 0)
                tableRowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 0));
                int filteredRows = mainTable.getRowCount();
                statusLabel.setText("Filtered: " + filteredRows + " items shown");
            } catch (java.util.regex.PatternSyntaxException e) {
                log.warn("Invalid regex pattern in filter", e);
                statusLabel.setText("Invalid filter pattern");
            }
        }
    }

    private JPanel createStackSelectionPanel() {
        JPanel stackPanel = new JPanel(new GridBagLayout());
        stackPanel.setBorder(BorderFactory.createTitledBorder("Failstack Selection"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // MON stack selection
        stackPanel.add(new JLabel("PRI Stack:"), gbc);
        gbc.gridx++;
        monStackCombo = new JComboBox<>(AccessoryStack.values());
        monStackCombo.setSelectedItem(calculator.getMonStack());
        monStackCombo.setRenderer(new StackComboRenderer());
        monStackCombo.addActionListener(e -> calculator.setMonStack((AccessoryStack) monStackCombo.getSelectedItem()));
        stackPanel.add(monStackCombo, gbc);

        // DUO stack selection
        gbc.gridx++;
        stackPanel.add(new JLabel("DUO Stack:"), gbc);
        gbc.gridx++;
        duoStackCombo = new JComboBox<>(AccessoryStack.values());
        duoStackCombo.setSelectedItem(calculator.getDuoStack());
        duoStackCombo.setRenderer(new StackComboRenderer());
        duoStackCombo.addActionListener(e -> calculator.setDuoStack((AccessoryStack) duoStackCombo.getSelectedItem()));
        stackPanel.add(duoStackCombo, gbc);

        // TRI stack selection
        gbc.gridx++;
        stackPanel.add(new JLabel("TRI Stack:"), gbc);
        gbc.gridx++;
        triStackCombo = new JComboBox<>(AccessoryStack.values());
        triStackCombo.setSelectedItem(calculator.getTriStack());
        triStackCombo.setRenderer(new StackComboRenderer());
        triStackCombo.addActionListener(e -> calculator.setTriStack((AccessoryStack) triStackCombo.getSelectedItem()));
        stackPanel.add(triStackCombo, gbc);

        // TET stack selection
        gbc.gridx++;
        stackPanel.add(new JLabel("TET Stack:"), gbc);
        gbc.gridx++;
        tetStackCombo = new JComboBox<>(AccessoryStack.values());
        tetStackCombo.setSelectedItem(calculator.getTetStack());
        tetStackCombo.setRenderer(new StackComboRenderer());
        tetStackCombo.addActionListener(e -> calculator.setTetStack((AccessoryStack) tetStackCombo.getSelectedItem()));
        stackPanel.add(tetStackCombo, gbc);

        return stackPanel;
    }

    // Custom renderer for stack combo boxes to show more useful information
    @Slf4j
    private static class StackComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof AccessoryStack stack) {
                String name = stack.name();
                setText(NameResolver.getDisplayNameForStack(name));
            }

            return c;
        }
    }

    private void setupTableSelectionListener() {
        // Add a selection listener to enable/disable the optimizeSelectedButton
        mainTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { // Only respond to final selection events
                boolean hasSelection = mainTable.getSelectedRowCount() > 0;
                // Only enable the button if market data is loaded AND there's a selection
                boolean shouldEnable = hasSelection && marketAccessories != null && !marketAccessories.isEmpty();
                if (optimizeSelectedButton != null) {
                    optimizeSelectedButton.setEnabled(shouldEnable);
                }
            }
        });
    }

    private void createMainTable() {
        // Create table with the required columns
        DefaultTableModel model = getDefaultTableModel();

        mainTable = new JTable(model);
        mainTable.setRowHeight(25);
        setupTableSelectionListener();

        // Use TableRowSorter for both sorting and filtering
        tableRowSorter = new TableRowSorter<>(model);
        mainTable.setRowSorter(tableRowSorter);

        // Set default sorting to TRI Profit column (index 5) in descending order
        tableRowSorter.setSortKeys(List.of(new RowSorter.SortKey(5, SortOrder.ASCENDING)));

        // Make first click on any column header sort in descending order
        for (int i = 0; i < mainTable.getColumnCount(); i++) {
            tableRowSorter.setSortsOnUpdates(true);
            tableRowSorter.setComparator(i, (o1, o2) -> {
                if (o1 instanceof Comparable && o2 instanceof Comparable) {
                    return ((Comparable) o2).compareTo(o1); // Reverse natural order
                }
                return 0;
            });
        }

        // Custom cell renderer for profit columns
        DefaultTableCellRenderer profitRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (value instanceof Long) {
                    long profit = (Long) value;
                    setHorizontalAlignment(SwingConstants.RIGHT);

                    if (!isSelected) {
                        if (profit < 0) {
                            c.setForeground(new Color(255, 87, 87)); // Bright red for negative profit
                        } else {
                            c.setForeground(new Color(85, 255, 85)); // Bright green for positive profit
                        }
                    }

                    setText(String.format("%,d", profit));
                }

                return c;
            }
        };

        // Apply renderers to profit columns
        mainTable.getColumnModel().getColumn(4).setCellRenderer(profitRenderer); // DUO Profit
        mainTable.getColumnModel().getColumn(5).setCellRenderer(profitRenderer); // TRI Profit
        mainTable.getColumnModel().getColumn(6).setCellRenderer(profitRenderer); // TET Profit
    }

    private static DefaultTableModel getDefaultTableModel() {
        String[] columnNames = {
                "Name",
                "DUO Items",
                "TRI Items",
                "TET Items",
                "DUO Profit",
                "TRI Profit",
                "TET Profit"
        };

        // Item count columns
        // Profit columns
        return new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if (column >= 1 && column <= 3) { // Item count columns
                    return Integer.class;
                } else if (column >= 4) { // Profit columns
                    return Long.class;
                }
                return String.class;
            }
        };
    }

    private void loadMarketData() {
        // Disable both buttons during loading
        loadMarketDataButton.setEnabled(false);
        calculateButton.setEnabled(false);
        optimizeButton.setEnabled(false);

        statusLabel.setText("Loading market data...");

        SwingWorker<List<Accessory>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Accessory> doInBackground() {
                BDOMarket market = new BDOMarket();
                market.setProgressCallback(statusText -> {
                    // Update status from background thread to EDT
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Market data: " + statusText));
                });

                return market.getAccessories();
            }

            @Override
            protected void done() {
                try {
                    marketAccessories = get();
                    statusLabel.setText("Market data loaded. " + marketAccessories.size() + " accessories found.");

                    // Setze die geladenen Daten im Calculator
                    calculator.setCachedAccessories(marketAccessories);

                    // Nach dem Laden direkt die Berechnung starten
                    calculateProfitsWithLoadedData();

                    // Enable both buttons
                    loadMarketDataButton.setEnabled(true);
                    calculateButton.setEnabled(true);
                    optimizeButton.setEnabled(true);
                    if (optimizeSelectedButton != null) {
                        optimizeSelectedButton.setEnabled(mainTable.getSelectedRowCount() > 0);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Error loading market data: " + e.getMessage());
                    log.error("Error loading market data", e);
                    loadMarketDataButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void calculateProfits() {
        // Prüfen ob Marktdaten geladen wurden
        if (marketAccessories == null || marketAccessories.isEmpty()) {
            statusLabel.setText("Please load market data first!");
            return;
        }

        // UI deaktivieren
        calculateButton.setEnabled(false);
        optimizeButton.setEnabled(false);
        if (optimizeSelectedButton != null) {
            optimizeSelectedButton.setEnabled(false);
        }

        // Direkt zur Berechnung fortfahren
        calculateProfitsWithLoadedData();
    }

    private void calculateProfitsWithLoadedData() {
        // Update the calculator with the current stack selections
        calculator.setMonStack((AccessoryStack) monStackCombo.getSelectedItem());
        calculator.setDuoStack((AccessoryStack) duoStackCombo.getSelectedItem());
        calculator.setTriStack((AccessoryStack) triStackCombo.getSelectedItem());
        calculator.setTetStack((AccessoryStack) tetStackCombo.getSelectedItem());

        // Set the number of simulation runs
        calculator.setSimulationRuns((Integer) simulationRunsSpinner.getValue());

        // Display which stacks are being used in the status
        statusLabel.setText(String.format("Calculating with PRI:%s DUO:%s TRI:%s TET:%s, Runs: %,d",
                Objects.requireNonNull(NameResolver.getDisplayNameForStack(Objects.requireNonNull(monStackCombo.getSelectedItem()).toString())),
                Objects.requireNonNull(NameResolver.getDisplayNameForStack(Objects.requireNonNull(duoStackCombo.getSelectedItem()).toString())),
                Objects.requireNonNull(NameResolver.getDisplayNameForStack(Objects.requireNonNull(triStackCombo.getSelectedItem()).toString())),
                Objects.requireNonNull(NameResolver.getDisplayNameForStack(Objects.requireNonNull(tetStackCombo.getSelectedItem()).toString())),
                calculator.getSimulationRuns()));

        // Set up progress callback
        calculator.setProgressCallback(statusText -> {
            // Update status from background thread to EDT
            SwingUtilities.invokeLater(() -> statusLabel.setText(statusText));
        });

        SwingWorker<List<AccessoryResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<AccessoryResult> doInBackground() {
                if (marketAccessories != null && !marketAccessories.isEmpty()) {
                    // Benutze die vorhandenen Daten direkt für die Berechnung
                    return calculator.calculateProfitsWithAccessories(marketAccessories);
                } else {
                    // Fallback, falls aus irgendeinem Grund keine Daten vorhanden sind
                    return calculator.calculateProfits();
                }
            }

            @Override
            protected void done() {
                try {
                    results = get();
                    updateTable();
                    statusLabel.setText("Calculation complete");

                    // Reapply filter if one exists
                    if (!filterTextField.getText().trim().isEmpty()) {
                        updateFilter();
                    }
                } catch (Exception e) {
                    statusLabel.setText("Calculation error: " + e.getMessage());
                    log.error("Error calculating profits", e);
                } finally {
                    // Re-enable the buttons
                    calculateButton.setEnabled(true);
                    loadMarketDataButton.setEnabled(true);
                    optimizeButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void updateTable() {
        if (results == null || results.isEmpty()) {
            log.warn("No results to display");
            return;
        }

        DefaultTableModel model = (DefaultTableModel) mainTable.getModel();
        model.setRowCount(0);

        for (AccessoryResult result : results) {
            model.addRow(new Object[]{
                    result.name,
                    result.duoItems,
                    result.triItems,
                    result.tetItems,
                    result.duoProfit,
                    result.triProfit,
                    result.tetProfit
            });
        }

        // Force table repaint to ensure proper formatting
        mainTable.repaint();
    }

    /**
     * Diese Methode aktualisiert die Statusmeldung
     */
    public void updateStatus(String message) {
        statusLabel.setText(message);
    }

    /**
     * Run optimization on selected accessories only
     */
    private void optimizeSelectedAccessories(List<Accessory> selectedAccessories) {
        if (selectedAccessories.isEmpty()) {
            statusLabel.setText("No accessories selected for optimization");
            return;
        }

        // Inform user about the optimization
        statusLabel.setText("Optimizing stacks for " + selectedAccessories.size() + " selected accessories...");

        // Create and show modified OptimalStackDialog for selected accessories
        OptimalStackDialog.optimizeAndShowDialog(this, selectedAccessories, this);
    }

    public static void main(String[] args) {
        // Initialize FlatLaf before creating any Swing components
        FlatDarkLaf.setup();

        SwingUtilities.invokeLater(() -> {
            try {
                EnhanceProfitGUI gui = new EnhanceProfitGUI();
                gui.setVisible(true);
            } catch (Exception e) {
                log.error("Failed to start application", e);
                JOptionPane.showMessageDialog(null,
                        "Application failed to start: " + e.getMessage(),
                        "Startup Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}