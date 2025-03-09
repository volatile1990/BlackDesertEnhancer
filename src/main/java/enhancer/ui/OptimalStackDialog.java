package enhancer.ui;

import enhancer.calculator.OptimalStackCalculator;
import enhancer.models.AccessoryStack;
import enhancer.models.OptimalStackResult;
import enhancer.models.market.Accessory;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;

/**
 * Dialog zur Anzeige der optimalen Stacks (optimiert für TRI)
 * Vereinfachte Version ohne Select/Apply-Logik
 */
@Slf4j
public class OptimalStackDialog extends JDialog {

    private final List<OptimalStackResult> optimalResults;

    /**
     * Konstruktor für den Dialog
     *
     * @param owner Parent-Frame
     * @param optimalResults Liste der optimalen Stack-Ergebnisse
     * @param isSelectionBased True wenn nur ausgewählte Accessoires optimiert wurden
     */
    public OptimalStackDialog(Frame owner, List<OptimalStackResult> optimalResults, boolean isSelectionBased) {
        super(owner,
                isSelectionBased ?
                        "Optimal Stack Combinations - Selected Accessories (TRI Optimized)" :
                        "Optimal Stack Combinations (TRI Optimized)",
                true);
        this.optimalResults = optimalResults;

        setSize(750, 500);
        setLocationRelativeTo(owner);

        // Hauptpanel mit BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Infotext am oberen Rand - angepasst für Selektions-basierte Optimierung
        String infoText = isSelectionBased ?
                "<html>The table below shows the optimal failstack combinations for your <b>selected accessories</b> " +
                        "to maximize <b>TRI profit</b>. These are only recommendations and " +
                        "may vary based on market conditions.</html>" :
                "<html>The table below shows the optimal failstack combinations " +
                        "for each accessory to maximize <b>TRI profit</b>. These are only recommendations and " +
                        "may vary based on market conditions.</html>";

        JLabel infoLabel = new JLabel(infoText);
        mainPanel.add(infoLabel, BorderLayout.NORTH);

        // Tabelle für Ergebnisse
        JTable resultsTable = createResultsTable();
        JScrollPane scrollPane = new JScrollPane(resultsTable);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Schließen-Button am unteren Rand
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    /**
     * Erstellt die Ergebnistabelle
     */
    private JTable createResultsTable() {
        // Spaltenüberschriften
        String[] columnNames = {
                "Accessory", "PRI Stack", "DUO Stack", "TRI Stack", "TRI Profit"
        };

        // Tabellenmodell mit nicht editierbaren Zellen
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Alle Zellen nicht editierbar
            }

            @Override
            public Class<?> getColumnClass(int column) {
                // Wichtig für korrekte Sortierung: TRI Profit als Long behandeln
                if (column == 4) return Long.class;
                return String.class;
            }
        };

        // Füge Daten zur Tabelle hinzu
        for (OptimalStackResult result : optimalResults) {
            model.addRow(new Object[] {
                    result.getAccessoryName(),
                    formatStackName(result.getOptimalPriStack()),
                    formatStackName(result.getOptimalDuoStack()),
                    formatStackName(result.getOptimalTriStack()),
                    result.getTotalProfit() // Wichtig: Rohe Long-Werte für Sortierung verwenden
            });
        }

        // Erstelle Tabelle
        JTable table = new JTable(model);
        table.setRowHeight(25);

        // Konfiguriere TableRowSorter für korrekte Sortierung
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        // Spezielle Comparators für jede Spalte definieren
        sorter.setComparator(0, String.CASE_INSENSITIVE_ORDER); // Accessory Name

        // Als Standard nach TRI Profit absteigend sortieren (Spaltenindex 4)
        List<RowSorter.SortKey> sortKeys = List.of(
                new RowSorter.SortKey(4, SortOrder.DESCENDING)
        );
        sorter.setSortKeys(sortKeys);
        sorter.sort();

        // Anpassen der Spaltenbreiten
        table.getColumnModel().getColumn(0).setPreferredWidth(200); // Accessory
        table.getColumnModel().getColumn(1).setPreferredWidth(100); // PRI
        table.getColumnModel().getColumn(2).setPreferredWidth(100); // DUO
        table.getColumnModel().getColumn(3).setPreferredWidth(100); // TRI
        table.getColumnModel().getColumn(4).setPreferredWidth(120); // TRI Profit

        // Renderer für Profit-Spalte
        DefaultTableCellRenderer profitRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                // Format als Zahl mit Tausender-Trennzeichen
                if (value instanceof Long) {
                    value = String.format("%,d", (Long) value);
                }

                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                setHorizontalAlignment(SwingConstants.RIGHT);

                // Farbe basierend auf Profit
                if (!isSelected && value instanceof String) {
                    String valueStr = ((String) value).replace(",", "");
                    try {
                        long profit = Long.parseLong(valueStr);
                        if (profit < 0) {
                            c.setForeground(new Color(255, 87, 87)); // Rot für negativen Profit
                        } else {
                            c.setForeground(new Color(85, 255, 85)); // Grün für positiven Profit
                        }
                    } catch (NumberFormatException e) {
                        // Ignorieren, wenn keine Zahl
                    }
                }

                return c;
            }
        };

        // Anwenden des Renderers
        table.getColumnModel().getColumn(4).setCellRenderer(profitRenderer);

        return table;
    }

    /**
     * Formatiert den Stack-Namen für die Anzeige
     */
    private String formatStackName(AccessoryStack stack) {
        return NameResolver.getDisplayNameForStack(stack.name());
    }

    /**
     * Methode zum Ausführen der Stack-Optimierung und Anzeigen des Dialogs
     *
     * @param parent Parent-Frame
     * @param accessories Liste der Accessoires
     * @param parentGUI Referenz zur Haupt-GUI
     * @param isSelectionBased True wenn nur ausgewählte Accessoires optimiert werden
     */
    public static void optimizeAndShowDialog(Frame parent, List<Accessory> accessories,
                                             EnhanceProfitGUI parentGUI, boolean isSelectionBased) {
        // Deaktiviere den Parent-Frame während der Berechnung
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Starte die Berechnung in einem separaten Thread
        SwingWorker<List<OptimalStackResult>, String> worker = new SwingWorker<>() {
            @Override
            protected List<OptimalStackResult> doInBackground() {
                OptimalStackCalculator calculator = new OptimalStackCalculator();

                // Statusmeldungen an die GUI weiterleiten
                return calculator.findOptimalStacks(accessories, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                // Zeige die neueste Statusmeldung in der GUI an
                if (!chunks.isEmpty()) {
                    parentGUI.updateStatus(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    // Ergebnisse holen
                    List<OptimalStackResult> results = get();

                    // Dialog anzeigen, jetzt mit isSelectionBased Parameter
                    OptimalStackDialog dialog = new OptimalStackDialog(parent, results, isSelectionBased);
                    dialog.setVisible(true);

                } catch (Exception e) {
                    log.error("Error during stack optimization", e);
                    JOptionPane.showMessageDialog(parent,
                            "Error optimizing stacks: " + e.getMessage(),
                            "Optimization Error",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    // Parent-Frame wieder aktivieren
                    parent.setCursor(Cursor.getDefaultCursor());
                    parentGUI.updateStatus("Ready");
                }
            }
        };

        worker.execute();
    }

    /**
     * Methode zum Ausführen der Stack-Optimierung und Anzeigen des Dialogs
     * (Legacy-Methode ohne isSelectionBased-Parameter)
     *
     * @param parent Parent-Frame
     * @param accessories Liste der Accessoires
     * @param parentGUI Referenz zur Haupt-GUI
     */
    public static void optimizeAndShowDialog(Frame parent, List<Accessory> accessories, EnhanceProfitGUI parentGUI) {
        optimizeAndShowDialog(parent, accessories, parentGUI, false);
    }

    /**
     * Methode zum Hinzufügen des Optimize-Stacks-Buttons zum Control-Panel
     *
     * @param controlPanel Das Panel, zu dem der Button hinzugefügt werden soll
     * @param parent Parent-Frame für den Dialog
     * @param parentGUI Referenz zur Haupt-GUI
     * @param getAccessoriesFunc Funktion, die die aktuelle Liste der Accessoires liefert
     */
    public static void addOptimizeButton(JPanel controlPanel, Frame parent, EnhanceProfitGUI parentGUI,
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