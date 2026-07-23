import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.io.*;
import java.util.List;

public class SmartParkingSystemGUI extends JFrame {

    private static final double BASE_PRICE_PER_HOUR = 50.0;
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat PRICE_FMT = new DecimalFormat("₹0.00");
    private static final String FILE_NAME = "bookings.dat"; // file to persist history

    // Inner classes
    static class Slot implements Serializable {
        int id;
        boolean occupied;
        Integer bookingId;

        Slot(int id) {
            this.id = id;
            this.occupied = false;
            this.bookingId = null;
        }
    }

    static class Booking implements Serializable {
        int bookingId;
        int slotId;
        String vehicleNumber;
        int hours;
        double pricePerHourAtBooking;
        double totalPrice;
        Date startTime;

        Booking(int bookingId, int slotId, String vehicleNumber, int hours, double pricePerHourAtBooking) {
            this.bookingId = bookingId;
            this.slotId = slotId;
            this.vehicleNumber = vehicleNumber;
            this.hours = hours;
            this.pricePerHourAtBooking = pricePerHourAtBooking;
            this.totalPrice = pricePerHourAtBooking * hours;
            this.startTime = new Date();
        }
    }

    private final int totalSlots;
    private final Map<Integer, Slot> slots = new LinkedHashMap<>();
    private final Map<Integer, Booking> bookings = new LinkedHashMap<>();
    private int nextBookingId = 1001;

    private JLabel lblTitle, lblAvailability, lblPrice;
    private JTable tblBookings;
    private DefaultTableModel bookingModel;
    private boolean darkMode = false;

    // Constructor
    public SmartParkingSystemGUI(int totalSlots) {
        this.totalSlots = totalSlots;
        // Initialize slots
        for (int i = 1; i <= totalSlots; i++) {
            slots.put(i, new Slot(i));
        }

        // Load history from file if exists
        loadBookingsFromFile();

        // Initialize nextBookingId to max existing + 1
        if (!bookings.isEmpty()) {
            nextBookingId = Collections.max(bookings.keySet()) + 1;
            // update slot occupied info based on loaded bookings
            for (Booking b : bookings.values()) {
                Slot s = slots.get(b.slotId);
                if (s != null) {
                    s.occupied = true;
                    s.bookingId = b.bookingId;
                }
            }
        }

        // === Setup Frame ===
        setTitle("Smart Parking System");
        setSize(950, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        lblTitle = new JLabel("Smart Parking System", SwingConstants.CENTER);
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 26));
        headerPanel.add(lblTitle, BorderLayout.NORTH);

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 6));
        lblAvailability = new JLabel();
        lblPrice = new JLabel();
        lblAvailability.setFont(new Font("SansSerif", Font.BOLD, 14));
        lblPrice.setFont(new Font("SansSerif", Font.PLAIN, 13));
        infoPanel.add(lblAvailability);
        infoPanel.add(lblPrice);
        headerPanel.add(infoPanel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        // Table
        bookingModel = new DefaultTableModel(
                new Object[]{"Booking ID", "Slot", "Vehicle", "Hours", "Price/hr", "Total", "Start Time"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // read-only
            }
        };
        tblBookings = new JTable(bookingModel);
        tblBookings.setFillsViewportHeight(true);
        tblBookings.setRowHeight(24);
        tblBookings.getTableHeader().setReorderingAllowed(false);

        // Center numeric columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        tblBookings.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tblBookings.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        tblBookings.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);

        // Price renderer
        DefaultTableCellRenderer priceRenderer = new DefaultTableCellRenderer() {
            @Override
            public void setValue(Object value) {
                if (value instanceof Number) {
                    setText(PRICE_FMT.format(((Number) value).doubleValue()));
                } else {
                    setText(value == null ? "" : value.toString());
                }
            }
        };
        priceRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        tblBookings.getColumnModel().getColumn(4).setCellRenderer(priceRenderer);
        tblBookings.getColumnModel().getColumn(5).setCellRenderer(priceRenderer);

        JScrollPane scroll = new JScrollPane(tblBookings);
        add(scroll, BorderLayout.CENTER);

        // Button panel
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        JButton btnCreate = new JButton("Create Booking");
        JButton btnUpdate = new JButton("Update Booking");
        JButton btnDispatch = new JButton("Dispatch Booking");
        JButton btnRefresh = new JButton("Refresh");
        JButton btnFreeSlots = new JButton("View Free Slots");
        JToggleButton toggleTheme = new JToggleButton("Dark Mode");
        JButton btnHistory = new JButton("View History");

        btnPanel.add(btnCreate);
        btnPanel.add(btnUpdate);
        btnPanel.add(btnDispatch);
        btnPanel.add(btnRefresh);
        btnPanel.add(btnFreeSlots);
        btnPanel.add(toggleTheme);
        btnPanel.add(btnHistory);

        add(btnPanel, BorderLayout.SOUTH);

        // Button actions
        btnCreate.addActionListener(e -> createBooking());
        btnUpdate.addActionListener(e -> updateBooking());
        btnDispatch.addActionListener(e -> dispatchBooking());
        btnRefresh.addActionListener(e -> refreshData());
        btnFreeSlots.addActionListener(e -> showFreeSlots());
        toggleTheme.addItemListener(e -> {
            darkMode = (e.getStateChange() == ItemEvent.SELECTED);
            applyTheme(darkMode);
        });
        btnHistory.addActionListener(e -> showHistoryWindow());

        applyTheme(darkMode);
        refreshData();
        setVisible(true);
    }

    // Core logic
    private double currentAvailabilityRatio() {
        long free = slots.values().stream().filter(s -> !s.occupied).count();
        return (double) free / totalSlots;
    }

    private double currentPricePerHour() {
        double ratio = currentAvailabilityRatio();
        double multiplier = 1.0 + (1.0 - ratio);
        double price = BASE_PRICE_PER_HOUR * multiplier;
        return Math.round(price * 100.0) / 100.0;
    }

    private Slot findFirstFreeSlot() {
        for (Slot s : slots.values()) {
            if (!s.occupied) return s;
        }
        return null;
    }

    private void createBooking() {
        Slot slot = findFirstFreeSlot();
        if (slot == null) {
            JOptionPane.showMessageDialog(this, "No available slots!");
            return;
        }

        JTextField vehicleField = new JTextField();
        JTextField hoursField = new JTextField();

        Object[] message = {
                "Vehicle Number:", vehicleField,
                "Hours:", hoursField
        };

        int option = JOptionPane.showConfirmDialog(this, message, "New Booking", JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) return;

        String vehicle = vehicleField.getText().trim();
        int hours;
        try {
            hours = Integer.parseInt(hoursField.getText().trim());
            if (hours <= 0) hours = 1;
        } catch (NumberFormatException ex) {
            hours = 1;
        }

        double pricePerHour = currentPricePerHour();
        Booking b = new Booking(nextBookingId++, slot.id, vehicle.isEmpty() ? "UNKNOWN" : vehicle, hours, pricePerHour);

        slot.occupied = true;
        slot.bookingId = b.bookingId;
        bookings.put(b.bookingId, b);

        saveBookingsToFile();
        refreshData();
    }

    private void updateBooking() {
        String idStr = JOptionPane.showInputDialog(this, "Enter Booking ID to update:");
        if (idStr == null || idStr.isEmpty()) return;

        int id;
        try { id = Integer.parseInt(idStr); } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid ID");
            return;
        }

        Booking b = bookings.get(id);
        if (b == null) { JOptionPane.showMessageDialog(this, "Booking not found!"); return; }

        JTextField hoursField = new JTextField(String.valueOf(b.hours));
        JTextField vehicleField = new JTextField(b.vehicleNumber);

        Object[] message = {
                "Hours:", hoursField,
                "Vehicle Number:", vehicleField
        };

        int option = JOptionPane.showConfirmDialog(this, message, "Update Booking", JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) return;

        try {
            int newHours = Integer.parseInt(hoursField.getText().trim());
            if (newHours > 0) {
                b.hours = newHours;
                b.pricePerHourAtBooking = currentPricePerHour();
                b.totalPrice = Math.round(b.pricePerHourAtBooking * b.hours * 100.0) / 100.0;
            }
        } catch (NumberFormatException ignored) {}

        if (!vehicleField.getText().trim().isEmpty()) b.vehicleNumber = vehicleField.getText().trim();

        saveBookingsToFile();
        refreshData();
    }

    private void dispatchBooking() {
        String idStr = JOptionPane.showInputDialog(this, "Enter Booking ID to dispatch:");
        if (idStr == null || idStr.isEmpty()) return;

        int id;
        try { id = Integer.parseInt(idStr); } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid ID");
            return;
        }

        Booking b = bookings.get(id);
        if (b == null) { JOptionPane.showMessageDialog(this, "Booking not found!"); return; }

        Slot s = slots.get(b.slotId);
        if (s != null) { s.occupied = false; s.bookingId = null; }

        bookings.remove(id);
        saveBookingsToFile();
        refreshData();
    }

    private void refreshData() {
        long free = slots.values().stream().filter(s -> !s.occupied).count();
        lblAvailability.setText(String.format("Total Slots: %d   |   Occupied: %d   |   Free: %d",
                totalSlots, totalSlots - free, free));
        lblPrice.setText(String.format("Current Dynamic Price/hr: %s", PRICE_FMT.format(currentPricePerHour())));

        bookingModel.setRowCount(0);
        for (Booking b : bookings.values()) {
            bookingModel.addRow(new Object[]{
                    b.bookingId, b.slotId, b.vehicleNumber, b.hours,
                    b.pricePerHourAtBooking, b.totalPrice, TIME_FMT.format(b.startTime)
            });
        }
    }

    private void showFreeSlots() {
        List<Integer> freeSlots = new ArrayList<>();
        for (Slot s : slots.values()) if (!s.occupied) freeSlots.add(s.id);

        String message = freeSlots.isEmpty() ? "No free slots available!" : "Free Slots: " + freeSlots.toString();
        JOptionPane.showMessageDialog(this, message, "Available Slots", JOptionPane.INFORMATION_MESSAGE);
    }

    private void applyTheme(boolean dark) {
        Color bg = dark ? new Color(43, 43, 43) : Color.WHITE;
        Color panelBg = dark ? new Color(60, 63, 65) : new Color(245, 245, 245);
        Color fg = dark ? Color.WHITE : Color.DARK_GRAY;
        Color buttonBg = dark ? new Color(75, 110, 175) : new Color(220, 220, 220);

        getContentPane().setBackground(bg);

        lblTitle.setForeground(fg);
        lblAvailability.setForeground(fg);
        lblPrice.setForeground(fg);

        tblBookings.setBackground(panelBg);
        tblBookings.setForeground(fg);
        tblBookings.getTableHeader().setBackground(buttonBg);
        tblBookings.getTableHeader().setForeground(Color.BLACK);
        tblBookings.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        tblBookings.setGridColor(dark ? Color.DARK_GRAY : new Color(200, 200, 200));

        SwingUtilities.invokeLater(() -> {
            for (Component c : getContentPane().getComponents()) {
                styleComponent(c, bg, panelBg, fg, buttonBg);
            }
        });
        repaint();
    }

    private void styleComponent(Component c, Color bg, Color panelBg, Color fg, Color buttonBg) {
        if (c instanceof JPanel) {
            c.setBackground(panelBg);
            for (Component child : ((JPanel) c).getComponents()) styleComponent(child, bg, panelBg, fg, buttonBg);
        } else if (c instanceof JScrollPane) {
            ((JScrollPane) c).getViewport().setBackground(panelBg);
            c.setBackground(panelBg);
        } else if (c instanceof JLabel) {
            c.setForeground(fg);
            c.setBackground(panelBg);
        } else if (c instanceof JButton || c instanceof JToggleButton) {
            c.setBackground(buttonBg);
            c.setForeground(Color.BLACK);
            ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            c.setFont(new Font("SansSerif", Font.PLAIN, 12));
        } else if (c instanceof JTable) {
            c.setBackground(panelBg);
            c.setForeground(fg);
        }
    }

    // === Persistent history methods ===
    private void saveBookingsToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_NAME))) {
            oos.writeObject(bookings);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadBookingsFromFile() {
        File f = new File(FILE_NAME);
        if (f.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                Map<Integer, Booking> loaded = (Map<Integer, Booking>) ois.readObject();
                bookings.putAll(loaded);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // === History Window ===
    private void showHistoryWindow() {
        JFrame historyFrame = new JFrame("Booking History");
        historyFrame.setSize(800, 400);
        historyFrame.setLocationRelativeTo(this);
        historyFrame.setLayout(new BorderLayout(8, 8));

        DefaultTableModel historyModel = new DefaultTableModel(
                new Object[]{"Booking ID", "Slot", "Vehicle", "Hours", "Price/hr", "Total", "Start Time"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable tblHistory = new JTable(historyModel);
        JScrollPane scrollPane = new JScrollPane(tblHistory);
        historyFrame.add(scrollPane, BorderLayout.CENTER);

        // Load from bookings map
        for (Booking b : bookings.values()) {
            historyModel.addRow(new Object[]{
                    b.bookingId, b.slotId, b.vehicleNumber, b.hours,
                    b.pricePerHourAtBooking, b.totalPrice, TIME_FMT.format(b.startTime)
            });
        }

        JPanel btnPanel = new JPanel(new FlowLayout());
        JButton btnDeleteSelected = new JButton("Delete Selected");
        JButton btnDeleteAll = new JButton("Delete All");
        JButton btnClose = new JButton("Close");
        btnPanel.add(btnDeleteSelected);
        btnPanel.add(btnDeleteAll);
        btnPanel.add(btnClose);
        historyFrame.add(btnPanel, BorderLayout.SOUTH);

        btnDeleteSelected.addActionListener(e -> {
            int row = tblHistory.getSelectedRow();
            if (row == -1) { JOptionPane.showMessageDialog(historyFrame, "Select a booking to delete."); return; }
            int bookingId = (int) historyModel.getValueAt(row, 0);
            bookings.remove(bookingId);
            slots.values().forEach(s -> { if (s.bookingId != null && s.bookingId == bookingId) { s.occupied = false; s.bookingId = null; } });
            saveBookingsToFile();
            historyModel.removeRow(row);
            refreshData();
        });

        btnDeleteAll.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(historyFrame, "Are you sure you want to delete all history?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                bookings.clear();
                slots.values().forEach(s -> { s.occupied = false; s.bookingId = null; });
                saveBookingsToFile();
                historyModel.setRowCount(0);
                refreshData();
            }
        });

        btnClose.addActionListener(e -> historyFrame.dispose());
        historyFrame.setVisible(true);
    }

    // === Main ===
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String input = JOptionPane.showInputDialog("Enter total number of slots (default 20):");
            int total;
            try {
                total = Integer.parseInt(input);
                if (total <= 0) total = 20;
            } catch (Exception e) { total = 20; }
            new SmartParkingSystemGUI(total);
        });
    }
}
