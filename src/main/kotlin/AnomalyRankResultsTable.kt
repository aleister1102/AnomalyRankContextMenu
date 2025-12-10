import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.ui.editor.EditorOptions
import burp.api.montoya.ui.editor.HttpRequestEditor
import burp.api.montoya.ui.editor.HttpResponseEditor
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import java.io.FileWriter
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter

class AnomalyRankResultsTable(private val api: MontoyaApi, private val results: List<ResultEntry>) :
        JFrame("Anomaly Rank Results") {

    data class ResultEntry(
            val rank: Int,
            val method: String,
            val url: String,
            val statusCode: Short,
            val requestResponse: HttpRequestResponse
    )

    private val tableModel: DefaultTableModel
    private val sorter: TableRowSorter<DefaultTableModel>
    private val searchField: JTextField

    // Editors
    private val requestEditor: HttpRequestEditor
    private val responseEditor: HttpResponseEditor

    init {
        // Modern Look and Feel setup
        layout = BorderLayout(10, 10)
        setSize(1100, 800) // Increased height for the split pane
        setLocationRelativeTo(null)

        // Main Content Panel with Padding
        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(15, 15, 15, 15)
        add(mainPanel, BorderLayout.CENTER)

        // --- Header Section (Title + Search) ---
        val topPanel = JPanel(BorderLayout(10, 10))
        val titleLabel = JLabel("Anomaly Analysis Results")
        titleLabel.font = Font("SansSerif", Font.BOLD, 18)
        // titleLabel.foreground = Color(50, 50, 50) // Removed hardcoded color for theme
        // compatibility

        val searchPanel = JPanel(FlowLayout(FlowLayout.RIGHT))

        // Add Filter Button & Menu
        val filterButton = JButton("Add Filter")
        val filterMenu = JPopupMenu()

        // --- Logic Operators ---
        val logicMenu = JMenu("Logic")
        val andItem = JMenuItem("AND")
        andItem.addActionListener { appendFilter(" AND ", true) }
        val orItem = JMenuItem("OR")
        orItem.addActionListener { appendFilter(" OR ", true) }
        logicMenu.add(andItem)
        logicMenu.add(orItem)

        // --- Fields ---
        val methodMenu = JMenu("Method")
        listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS").forEach { method ->
            val item = JMenuItem(method)
            item.addActionListener { appendFilter("method:$method", false) }
            methodMenu.add(item)
        }

        val statusMenu = JMenu("Status")
        listOf("200", "201", "301", "302", "400", "401", "403", "404", "500", "502", "503")
                .forEach { code ->
                    val item = JMenuItem(code)
                    item.addActionListener { appendFilter("status:$code", false) }
                    statusMenu.add(item)
                }

        val rankMenu = JMenu("Rank")
        listOf(">= 5", ">= 10", ">= 20", ">= 50", ">= 100").forEach { label ->
            val value = label.replace(" ", "")
            val item = JMenuItem(label)
            item.addActionListener { appendFilter("rank:$value", false) }
            rankMenu.add(item)
        }
        rankMenu.addSeparator()
        val rankCustomItem = JMenuItem("Custom...")
        rankCustomItem.addActionListener { appendFilter("rank:", false) }
        rankMenu.add(rankCustomItem)

        filterMenu.add(logicMenu)
        filterMenu.addSeparator()
        filterMenu.add(methodMenu)
        filterMenu.add(statusMenu)
        filterMenu.add(rankMenu)

        // Simple actions for fields that need input
        val urlItem = JMenuItem("URL (Custom)")
        urlItem.addActionListener { appendFilter("url:", false) }
        filterMenu.add(urlItem)

        filterButton.addActionListener { filterMenu.show(filterButton, 0, filterButton.height) }

        val searchLabel = JLabel("Filter:")
        searchField = JTextField(20)
        searchField.toolTipText =
                "Supports: field:value, AND, OR (e.g., method:POST AND status:200)"

        searchPanel.add(filterButton)
        searchPanel.add(searchLabel)
        searchPanel.add(searchField)

        topPanel.add(titleLabel, BorderLayout.WEST)
        topPanel.add(searchPanel, BorderLayout.EAST)
        mainPanel.add(topPanel, BorderLayout.NORTH)

        // --- Table Section ---
        val columnNames = arrayOf("Rank", "Method", "URL", "Status Code")
        tableModel =
                object : DefaultTableModel(columnNames, 0) {
                    override fun isCellEditable(row: Int, column: Int): Boolean = false
                    override fun getColumnClass(columnIndex: Int): Class<*> {
                        return when (columnIndex) {
                            0 -> Int::class.javaObjectType
                            3 -> Short::class.javaObjectType
                            else -> String::class.java
                        }
                    }
                }

        results.forEach { entry ->
            tableModel.addRow(arrayOf(entry.rank, entry.method, entry.url, entry.statusCode))
        }

        val table = JTable(tableModel)
        table.rowHeight = 25
        table.font = Font("SansSerif", Font.PLAIN, 13)
        table.intercellSpacing = Dimension(1, 1)
        table.showVerticalLines = true
        table.showHorizontalLines = true
        table.gridColor = Color.GRAY
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        // Custom Header
        table.tableHeader.font = Font("SansSerif", Font.BOLD, 13)
        table.tableHeader.preferredSize = Dimension(table.tableHeader.width, 30)

        // Custom Cell Renderer for Coloring
        val colorRenderer =
                object : DefaultTableCellRenderer() {
                    override fun getTableCellRendererComponent(
                            table: JTable?,
                            value: Any?,
                            isSelected: Boolean,
                            hasFocus: Boolean,
                            row: Int,
                            column: Int
                    ): Component {
                        val component =
                                super.getTableCellRendererComponent(
                                        table,
                                        value,
                                        isSelected,
                                        hasFocus,
                                        row,
                                        column
                                )

                        if (table != null) {
                            // Convert view row index to model index to get the correct data
                            val modelRow = table.convertRowIndexToModel(row)
                            val method = tableModel.getValueAt(modelRow, 1) as String
                            val statusCode = tableModel.getValueAt(modelRow, 3) as Short

                            if (!isSelected) {
                                if (column == 3) {
                                    component.background = getRowColor(statusCode.toInt(), method)
                                    component.foreground =
                                            Color.BLACK // Ensure text is readable on colored
                                    // background
                                } else {
                                    component.background = table.background
                                    component.foreground = table.foreground
                                }
                            } else {
                                // Keep selection color
                                component.background = table.selectionBackground
                                component.foreground = table.selectionForeground
                            }

                            // Center align Rank and Status Code
                            if (column == 0 || column == 3) {
                                horizontalAlignment = JLabel.CENTER
                            } else {
                                horizontalAlignment = JLabel.LEFT
                            }
                        }
                        return component
                    }
                }

        // Apply renderer to all columns
        for (i in 0 until table.columnCount) {
            table.columnModel.getColumn(i).cellRenderer = colorRenderer
        }

        // Column Widths
        table.columnModel.getColumn(0).preferredWidth = 50 // Rank
        table.columnModel.getColumn(0).maxWidth = 70

        table.columnModel.getColumn(1).preferredWidth = 70 // Method
        table.columnModel.getColumn(1).maxWidth = 100

        table.columnModel.getColumn(2).preferredWidth = 600 // URL (Expand)

        table.columnModel.getColumn(3).preferredWidth = 80 // Status Code
        table.columnModel.getColumn(3).maxWidth = 100

        // Sorting & Filtering
        sorter = TableRowSorter(tableModel)
        table.rowSorter = sorter

        searchField.addKeyListener(
                object : KeyAdapter() {
                    override fun keyReleased(e: KeyEvent?) {
                        updateFilter(searchField.text)
                    }
                }
        )

        val tableScrollPane = JScrollPane(table)
        tableScrollPane.border = BorderFactory.createLineBorder(Color(200, 200, 200))

        // --- Request/Response Viewer Section ---
        requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY)
        responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY)

        // Split view for Request and Response (Horizontal Split: Left/Right)
        val reqResSplitPane =
                JSplitPane(
                        JSplitPane.HORIZONTAL_SPLIT,
                        requestEditor.uiComponent(),
                        responseEditor.uiComponent()
                )
        reqResSplitPane.resizeWeight = 0.5 // Distribute space equally

        // --- Split Pane (Table Top / Editors Bottom) ---
        val mainSplitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScrollPane, reqResSplitPane)
        mainSplitPane.dividerLocation = 400 // Initial split position
        mainSplitPane.resizeWeight = 0.5 // Distribute extra space equally

        mainPanel.add(mainSplitPane, BorderLayout.CENTER)

        // --- Selection Listener ---
        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = table.selectedRow
                if (selectedRow != -1) {
                    // Convert view index to model index in case of sorting/filtering
                    val modelRow = table.convertRowIndexToModel(selectedRow)
                    // The 'results' list corresponds to the model rows by index IF we inserted them
                    // in order and didn't remove any.
                    // Since tableModel matches 'results' list order initially:
                    if (modelRow in results.indices) {
                        val entry = results[modelRow]
                        requestEditor.setRequest(entry.requestResponse.request())
                        responseEditor.setResponse(entry.requestResponse.response())
                    }
                } else {
                    // Clear editors if no selection
                    requestEditor.setRequest(
                            burp.api.montoya.http.message.requests.HttpRequest.httpRequest("")
                    )
                    responseEditor.setResponse(
                            burp.api.montoya.http.message.responses.HttpResponse.httpResponse("")
                    )
                }
            }
        }

        // --- Footer Section (Buttons) ---
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0))

        val exportButton = JButton("Export to CSV")
        exportButton.font = Font("SansSerif", Font.BOLD, 12)
        exportButton.background = Color(70, 130, 180)
        exportButton.isFocusPainted = false

        val closeButton = JButton("Close")
        closeButton.font = Font("SansSerif", Font.PLAIN, 12)

        exportButton.addActionListener { exportToCsv() }
        closeButton.addActionListener { dispose() }

        buttonPanel.add(exportButton)
        buttonPanel.add(closeButton)

        val statusLabel = JLabel("${results.size} items found")
        statusLabel.font = Font("SansSerif", Font.ITALIC, 11)

        val bottomContainer = JPanel(BorderLayout())
        bottomContainer.add(statusLabel, BorderLayout.WEST)
        bottomContainer.add(buttonPanel, BorderLayout.EAST)

        mainPanel.add(bottomContainer, BorderLayout.SOUTH)
    }

    private fun getRowColor(statusCode: Int, method: String): Color {
        return when {
            statusCode in 500..599 -> Color(255, 102, 102) // Red (Light Coral)
            statusCode in 400..499 -> Color(255, 178, 102) // Orange
            statusCode in 300..399 -> Color(255, 255, 153) // Yellow
            statusCode in 200..299 -> {
                when (method.uppercase()) {
                    "GET" -> Color(144, 238, 144) // Light Green
                    "POST" -> Color(173, 216, 230) // Light Blue
                    else -> Color(255, 182, 193) // Light Pink (PUT, PATCH, etc.)
                }
            }
            else -> Color.WHITE
        }
    }

    private fun appendFilter(text: String, isOperator: Boolean) {
        var current = searchField.text

        if (current.isNotEmpty() && !current.endsWith(" ")) {
            if (!isOperator) {
                // Auto-add AND if appending a new condition to an existing non-empty filter
                current += " AND "
            } else {
                current += " "
            }
        }

        searchField.text = current + text
        updateFilter(searchField.text)
        searchField.requestFocusInWindow()
    }

    private fun updateFilter(text: String) {
        if (text.trim().isEmpty()) {
            sorter.rowFilter = null
            return
        }

        try {
            sorter.rowFilter = parseFilter(text)
        } catch (e: Exception) {
            // Fallback to simple regex if parsing fails (or just ignore)
        }
    }

    private fun parseFilter(text: String): RowFilter<DefaultTableModel, Int>? {
        // Split by OR
        val orParts = text.split(Regex("\\s+OR\\s+", RegexOption.IGNORE_CASE))
        val orFilters = ArrayList<RowFilter<DefaultTableModel, Int>>()

        for (orPart in orParts) {
            // Split by AND
            val andParts = orPart.split(Regex("\\s+AND\\s+", RegexOption.IGNORE_CASE))
            val andFilters = ArrayList<RowFilter<DefaultTableModel, Int>>()

            for (token in andParts) {
                val trimmedToken = token.trim()
                if (trimmedToken.isNotEmpty()) {
                    val filter = createTokenFilter(trimmedToken)
                    if (filter != null) {
                        andFilters.add(filter)
                    }
                }
            }

            if (andFilters.isNotEmpty()) {
                if (andFilters.size == 1) {
                    orFilters.add(andFilters[0])
                } else {
                    orFilters.add(RowFilter.andFilter(andFilters))
                }
            }
        }

        if (orFilters.isEmpty()) return null
        if (orFilters.size == 1) return orFilters[0]
        return RowFilter.orFilter(orFilters)
    }

    private fun createTokenFilter(token: String): RowFilter<DefaultTableModel, Int>? {
        val parts = token.split(":", limit = 2)
        try {
            if (parts.size == 2) {
                val key = parts[0].trim().lowercase()
                val value = parts[1].trim()
                if (value.isEmpty()) return null

                val colIndex =
                        when (key) {
                            "rank" -> 0
                            "method" -> 1
                            "url", "path" -> 2
                            "status", "code", "statuscode" -> 3
                            else -> -1
                        }

                if (colIndex != -1) {
                    // Handle numeric comparisons for Rank (0) and Status (3)
                    if (colIndex == 0 || colIndex == 3) {
                        return createNumericFilter(colIndex, value)
                                ?: RowFilter.regexFilter("(?i)$value", colIndex)
                    }
                    return RowFilter.regexFilter("(?i)$value", colIndex)
                }
            }
            // Default: search everywhere
            return RowFilter.regexFilter("(?i)$token")
        } catch (e: java.util.regex.PatternSyntaxException) {
            return null
        }
    }

    private fun createNumericFilter(
            colIndex: Int,
            valueStr: String
    ): RowFilter<DefaultTableModel, Int>? {
        // Detect operator: >=, <=, >, <, or simple number
        val operator: String
        val numberPart: String

        if (valueStr.startsWith(">=")) {
            operator = ">="
            numberPart = valueStr.substring(2).trim()
        } else if (valueStr.startsWith("<=")) {
            operator = "<="
            numberPart = valueStr.substring(2).trim()
        } else if (valueStr.startsWith(">")) {
            operator = ">"
            numberPart = valueStr.substring(1).trim()
        } else if (valueStr.startsWith("<")) {
            operator = "<"
            numberPart = valueStr.substring(1).trim()
        } else {
            // Not a comparison filter, return null so regex fallback handles it
            return null
        }

        val targetValue = numberPart.toIntOrNull() ?: return null

        return object : RowFilter<DefaultTableModel, Int>() {
            override fun include(entry: Entry<out DefaultTableModel, out Int>): Boolean {
                val cellValue = entry.getValue(colIndex)
                val numValue =
                        when (cellValue) {
                            is Int -> cellValue
                            is Short -> cellValue.toInt()
                            is Number -> cellValue.toInt()
                            else -> cellValue.toString().toIntOrNull() ?: return false
                        }

                return when (operator) {
                    ">=" -> numValue >= targetValue
                    "<=" -> numValue <= targetValue
                    ">" -> numValue > targetValue
                    "<" -> numValue < targetValue
                    else -> false
                }
            }
        }
    }

    private fun exportToCsv() {
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "Save as CSV"
        fileChooser.selectedFile = File("anomaly_ranks.csv")

        val userSelection = fileChooser.showSaveDialog(this)

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            val fileToSave = fileChooser.selectedFile
            try {
                FileWriter(fileToSave).use { writer ->
                    writer.write("Rank,Method,URL,Status Code\n")
                    for (i in 0 until tableModel.rowCount) {
                        val rank = tableModel.getValueAt(i, 0)
                        val method = tableModel.getValueAt(i, 1)
                        val url = (tableModel.getValueAt(i, 2) as String).replace("\"", "\"\"")
                        val status = tableModel.getValueAt(i, 3)
                        writer.write("$rank,$method,\"$url\",$status\n")
                    }
                }
                JOptionPane.showMessageDialog(
                        this,
                        "Export successful!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                        this,
                        "Error saving file: ${e.message}",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
}
