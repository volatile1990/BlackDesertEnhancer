package com.bdo.enhancer.ui;

import com.bdo.enhancer.calculator.OptimalStackCalculator;
import com.bdo.enhancer.model.item.Accessory;
import com.bdo.enhancer.model.result.OptimalStackResult;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;

/**
 * Dialog for displaying optimal failstack combinations (optimized for TRI enhancement)
 * Simplified version without select/apply logic
 */
@Slf4j
public class OptimizationDialog extends JDialog {

    private final List<OptimalStackResult> optimalResults;

    /**
     * Constructor for the dialog
     *
     * @param owner Parent frame
     * @param optimalResults List of optimal stack results
     * @param isSelectionBased True if only selected accessories were optimized
     */
    public OptimizationDialog(Frame owner, List<OptimalStackResult> optimalResults, boolean isSelectionBased) {
        super(owner,
                isSelectionBased ?
                        "Optimal Stack Combinations - Selected Accessories (TRI Optimized)" :
                        "Optimal Stack Combinations (TRI Optimized)",
                true);
        this.optimalResults = optimalResults;

        setSize(750, 500);
        setLocationRelativeTo(owner);

        // Main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Info text at the top - adapted for selection-based optimization
        String infoText = isSelectionBased ?
                "<html>The table below shows the optimal failstack combinations for your <b>selected accessories</b> " +
                        "to maximize <b>TRI profit</b>. These are only recommendations and " +
                        "may vary based on market conditions.</html>" :
                "<html>The table below shows the optimal failstack combinations " +
                        "for each accessory to maximize <b>TRI profit</b>. These are only recommendations and " +
                        "may vary based on market conditions.</html>";

        JLabel infoLabel = new JLabel(infoText);
        mainPanel.add(infoLabel, BorderLayout.NORTH);

        // Results table
        JTable resultsTable = createResultsTable();
        JScrollPane scrollPane = new JScrollPane(resultsTable);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Close button at the bottom
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    /**
     * Creates the results table
     */
    private JTable createResultsTable() {
        // Column headers
        String[] columnNames = {
                "Accessory", "PRI Stack", "DUO Stack", "TRI Stack", "TRI Profit"
        };

        // Table model with non-editable cells
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // All cells non-editable
            }

            @Override
            public Class<?> getColumnClass(int column) {
                // Important for correct sorting: treat TRI Profit as Long
                if (column == 4) return Long.class;
                return String.class;
            }
        };

        // Add data to table
        for (OptimalStackResult result : optimalResults) {
            model.addRow(new Object[] {
                    result.getAccessoryName(),
                    result.getOptimalPriStack().getStackCount(),
                    result.getOptimalDuoStack().getStackCount(),
                    result.getOptimalTriStack().getStackCount(),
                    result.getTotalProfit() // Important: Use raw Long values for sorting
            });
        }

        // Create table
        JTable table = new JTable(model);
        table.setRowHeight(25);

        // Configure TableRowSorter for correct sorting
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        // Define special comparators for each column
        sorter.setComparator(0, String.CASE_INSENSITIVE_ORDER); // Accessory Name

        // Default sorting by TRI Profit descending (column index 4)
        List<RowSorter.SortKey> sortKeys = List.of(
                new RowSorter.SortKey(4, SortOrder.DESCENDING)
        );
        sorter.setSortKeys(sortKeys);
        sorter.sort();

        // Adjust column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(200); // Accessory
        table.getColumnModel().getColumn(1).setPreferredWidth(100); // PRI
        table.getColumnModel().getColumn(2).setPreferredWidth(100); // DUO
        table.getColumnModel().getColumn(3).setPreferredWidth(100); // TRI
        table.getColumnModel().getColumn(4).setPreferredWidth(120); // TRI Profit

        // Renderer for profit column
        DefaultTableCellRenderer profitRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                // Format as number with thousands separator
                if (value instanceof Long) {
                    value = String.format("%,d", (Long) value);
                }

                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                setHorizontalAlignment(SwingConstants.RIGHT);

                // Color based on profit
                if (!isSelected) {
                    long profit = 0;
                    if (value instanceof Long) {
                        profit = (Long) value;
                    } else if (value instanceof String) {
                        String valueStr = ((String) value).replace(",", "");
                        try {
                            profit = Long.parseLong(valueStr);
                        } catch (NumberFormatException e) {
                            // Ignore if not a number
                        }
                    }
                    
                    if (profit < 0) {
                        c.setForeground(new Color(255, 87, 87)); // Red for negative profit
                    } else {
                        c.setForeground(new Color(85, 255, 85)); // Green for positive profit
                    }
                }

                return c;
            }
        };

        // Apply the renderer
        table.getColumnModel().getColumn(4).setCellRenderer(profitRenderer);

        return table;
    }

    /**
     * Method to execute stack optimization and display the dialog
     *
     * @param parent Parent frame
     * @param accessories List of accessories
     * @param parentGUI Reference to the main GUI
     * @param isSelectionBased True if only selected accessories should be optimized
     */
    public static void optimizeAndShowDialog(Frame parent, List<Accessory> accessories,
                                             EnhancerMainFrame parentGUI, boolean isSelectionBased) {
        // Disable parent frame during calculation
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Start calculation in a separate thread
        SwingWorker<List<OptimalStackResult>, String> worker = new SwingWorker<>() {
            @Override
            protected List<OptimalStackResult> doInBackground() {
                OptimalStackCalculator calculator = new OptimalStackCalculator();

                // Forward status messages to the GUI
                return calculator.findOptimalStacks(accessories, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                // Show the latest status message in the GUI
                if (!chunks.isEmpty()) {
                    parentGUI.updateStatus(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    // Get results
                    List<OptimalStackResult> results = get();

                    // Show dialog with isSelectionBased parameter
                    OptimizationDialog dialog = new OptimizationDialog(parent, results, isSelectionBased);
                    dialog.setVisible(true);

                } catch (Exception e) {
                    log.error("Error during stack optimization", e);
                    JOptionPane.showMessageDialog(parent,
                            "Error optimizing stacks: " + e.getMessage(),
                            "Optimization Error",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    // Re-enable parent frame
                    parent.setCursor(Cursor.getDefaultCursor());
                    parentGUI.updateStatus("Ready");
                }
            }
        };

        worker.execute();
    }

    /**
     * Method to execute stack optimization and display the dialog
     * (Legacy method without isSelectionBased parameter)
     *
     * @param parent Parent frame
     * @param accessories List of accessories
     * @param parentGUI Reference to the main GUI
     */
    public static void optimizeAndShowDialog(Frame parent, List<Accessory> accessories, EnhancerMainFrame parentGUI) {
        optimizeAndShowDialog(parent, accessories, parentGUI, false);
    }

    /**
     * Method to add the optimize stacks button to the control panel
     *
     * @param controlPanel The panel to which the button should be added
     * @param parent Parent frame for the dialog
     * @param parentGUI Reference to the main GUI
     * @param getAccessoriesFunc Function that provides the current list of accessories
     */
    public static void addOptimizeButton(JPanel controlPanel, Frame parent, EnhancerMainFrame parentGUI,
                                         java.util.function.Supplier<List<Accessory>> getAccessoriesFunc) {
        JButton optimizeButton = new JButton("Optimize TRI Stacks");
        optimizeButton.setToolTipText("Find optimal failstack combinations for TRI enhancement");

        optimizeButton.addActionListener(e -> {
            List<Accessory> accessories = getAccessoriesFunc.get();
            if (accessories == null || accessories.isEmpty()) {
                JOptionPane.showMessageDialog(parent,
                        "Please load market data first!",
                        "No Data",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            optimizeAndShowDialog(parent, accessories, parentGUI);
        });

        controlPanel.add(optimizeButton);
    }
}