import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.regex.Pattern;

public class TenantApp extends JFrame {

    private MongoCollection<Document> col;
    private DefaultTableModel model;
    private JTable table;
    private JTextField txtFirst, txtLast, txtPhone, txtEmail, txtUnit,
                       txtProperty, txtRent, txtNationalID,
                       txtLeaseStart, txtLeaseEnd, txtSearch;
    private JComboBox<String> cboStatus;

    public TenantApp() {
        MongoClient client = MongoClients.create("mongodb://localhost:27017");
        col = client.getDatabase("TenantManagementDB")
                    .getCollection("tenants");

        setTitle("Tenant Management System - MongoDB CRUD");
        setSize(1250, 680);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // ---------- Header ----------
        JLabel title = new JLabel("  Tenant Management System", JLabel.LEFT);
        title.setOpaque(true);
        title.setBackground(new Color(28, 78, 128));
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setPreferredSize(new Dimension(0, 50));
        add(title, BorderLayout.NORTH);

        // ---------- Form ----------
        JPanel form = new JPanel(new GridLayout(11, 2, 6, 6));
        form.setBorder(BorderFactory.createTitledBorder("Tenant Details"));
        txtFirst = new JTextField();
        txtLast = new JTextField();
        txtPhone = new JTextField();
        txtEmail = new JTextField();
        txtUnit = new JTextField();
        txtProperty = new JTextField();
        txtRent = new JTextField();
        txtNationalID = new JTextField();
        txtLeaseStart = new JTextField();
        txtLeaseEnd = new JTextField();
        cboStatus = new JComboBox<>(new String[]{"Active", "Inactive"});

        form.add(new JLabel("First Name")); form.add(txtFirst);
        form.add(new JLabel("Last Name")); form.add(txtLast);
        form.add(new JLabel("Phone")); form.add(txtPhone);
        form.add(new JLabel("Email")); form.add(txtEmail);
        form.add(new JLabel("Unit Number")); form.add(txtUnit);
        form.add(new JLabel("Property Type")); form.add(txtProperty);
        form.add(new JLabel("Monthly Rent (UGX)")); form.add(txtRent);
        form.add(new JLabel("National ID")); form.add(txtNationalID);
        form.add(new JLabel("Lease Start (yyyy-mm-dd)")); form.add(txtLeaseStart);
        form.add(new JLabel("Lease End (yyyy-mm-dd)")); form.add(txtLeaseEnd);
        form.add(new JLabel("Status")); form.add(cboStatus);

        JPanel left = new JPanel(new BorderLayout());
        left.setPreferredSize(new Dimension(400, 0));
        left.add(form, BorderLayout.CENTER);

        // ---------- Buttons ----------
        JPanel buttons = new JPanel(new GridLayout(2, 3, 6, 6));
        buttons.add(makeButton("Add", new Color(39, 174, 96), e -> addTenant()));
        buttons.add(makeButton("Update", new Color(41, 128, 185), e -> updateTenant()));
        buttons.add(makeButton("Delete", new Color(192, 57, 43), e -> deleteTenant()));
        buttons.add(makeButton("Refresh", new Color(142, 68, 173), e -> loadData("")));
        buttons.add(makeButton("Clear Form", new Color(127, 140, 141), e -> clearForm()));
        left.add(buttons, BorderLayout.SOUTH);
        add(left, BorderLayout.WEST);

        // ---------- Table ----------
        model = new DefaultTableModel(new String[]{
            "ID", "First Name", "Last Name", "Phone", "Email", "Unit",
            "Property", "Rent (UGX)", "National ID",
            "Lease Start", "Lease End", "Status"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setBackground(new Color(28, 78, 128));
        table.getTableHeader().setForeground(Color.WHITE);
        table.removeColumn(table.getColumnModel().getColumn(0)); // hide ID
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) fillFormFromSelection();
        });

        // ---------- Search ----------
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtSearch = new JTextField(20);
        searchBar.add(new JLabel("Search name:"));
        searchBar.add(txtSearch);
        searchBar.add(makeButton("Search", new Color(28, 78, 128),
                      e -> loadData(txtSearch.getText().trim())));

        JPanel right = new JPanel(new BorderLayout());
        right.add(searchBar, BorderLayout.NORTH);
        right.add(new JScrollPane(table), BorderLayout.CENTER);
        add(right, BorderLayout.CENTER);

        loadData("");
    }

    private JButton makeButton(String text, Color bg, java.awt.event.ActionListener l) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.addActionListener(l);
        return b;
    }

    // ---------- READ ----------
    private void loadData(String filter) {
        model.setRowCount(0);
        FindIterable<Document> docs;
        if (filter.isEmpty()) {
            docs = col.find();
        } else {
            String p = Pattern.quote(filter);
            docs = col.find(Filters.or(
                Filters.regex("firstName", p, "i"),
                Filters.regex("lastName", p, "i")));
        }
        for (Document d : docs) {
            Object rent = d.get("monthlyRent");
            double r = (rent instanceof Number) ? ((Number) rent).doubleValue() : 0;
            model.addRow(new Object[]{
                d.getObjectId("_id").toHexString(),
                d.getString("firstName"), d.getString("lastName"),
                d.getString("phoneNumber"), d.getString("email"),
                d.getString("unitNumber"), d.getString("propertyType"),
                String.format("%,.0f", r),
                d.getString("nationalID"),
                d.getString("leaseStartDate"),
                d.getString("leaseEndDate"),
                d.getString("status")});
        }
    }

    // ---------- CREATE ----------
    private void addTenant() {
        Document d = readForm();
        if (d == null) return;
        col.insertOne(d);
        JOptionPane.showMessageDialog(this, "Tenant added successfully!");
        clearForm();
        loadData("");
    }

    // ---------- UPDATE ----------
    private void updateTenant() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a tenant in the table first.");
            return;
        }
        Document d = readForm();
        if (d == null) return;
        ObjectId id = new ObjectId((String) model.getValueAt(row, 0));
        col.updateOne(Filters.eq("_id", id), new Document("$set", d));
        JOptionPane.showMessageDialog(this, "Tenant updated successfully!");
        clearForm();
        loadData("");
    }

    // ---------- DELETE ----------
    private void deleteTenant() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a tenant in the table first.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this,
            "Delete this tenant permanently?", "Confirm Delete",
            JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        ObjectId id = new ObjectId((String) model.getValueAt(row, 0));
        col.deleteOne(Filters.eq("_id", id));
        clearForm();
        loadData("");
    }

    // ---------- Helpers ----------
    private Document readForm() {
        if (txtFirst.getText().trim().isEmpty()
            || txtLast.getText().trim().isEmpty()
            || txtUnit.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "First name, last name and unit are required.");
            return null;
        }
        double rent;
        try {
            rent = Double.parseDouble(txtRent.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid rent amount.");
            return null;
        }
        try {
            if (!txtLeaseStart.getText().trim().isEmpty())
                LocalDate.parse(txtLeaseStart.getText().trim());
            if (!txtLeaseEnd.getText().trim().isEmpty())
                LocalDate.parse(txtLeaseEnd.getText().trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Dates must look like 2026-11-13 (yyyy-mm-dd).");
            return null;
        }
        return new Document()
            .append("firstName", txtFirst.getText().trim())
            .append("lastName", txtLast.getText().trim())
            .append("phoneNumber", txtPhone.getText().trim())
            .append("email", txtEmail.getText().trim())
            .append("unitNumber", txtUnit.getText().trim())
            .append("propertyType", txtProperty.getText().trim())
            .append("monthlyRent", rent)
            .append("leaseStartDate", txtLeaseStart.getText().trim())
            .append("leaseEndDate", txtLeaseEnd.getText().trim())
            .append("nationalID", txtNationalID.getText().trim())
            .append("status", (String) cboStatus.getSelectedItem());
    }

    private void fillFormFromSelection() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        txtFirst.setText((String) model.getValueAt(row, 1));
        txtLast.setText((String) model.getValueAt(row, 2));
        txtPhone.setText((String) model.getValueAt(row, 3));
        txtEmail.setText((String) model.getValueAt(row, 4));
        txtUnit.setText((String) model.getValueAt(row, 5));
        txtProperty.setText((String) model.getValueAt(row, 6));
        txtRent.setText(((String) model.getValueAt(row, 7)).replace(",", ""));
        txtNationalID.setText((String) model.getValueAt(row, 8));
        txtLeaseStart.setText((String) model.getValueAt(row, 9));
        txtLeaseEnd.setText((String) model.getValueAt(row, 10));
        cboStatus.setSelectedItem(model.getValueAt(row, 11));
    }

    private void clearForm() {
        txtFirst.setText(""); txtLast.setText(""); txtPhone.setText("");
        txtEmail.setText(""); txtUnit.setText(""); txtProperty.setText("");
        txtRent.setText(""); txtNationalID.setText("");
        txtLeaseStart.setText(""); txtLeaseEnd.setText("");
        cboStatus.setSelectedIndex(0);
        table.clearSelection();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TenantApp().setVisible(true));
    }
}