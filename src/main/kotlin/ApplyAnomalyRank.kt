import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.ui.contextmenu.ContextMenuEvent
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider
import java.awt.Component
import javax.swing.JMenuItem
import javax.swing.SwingUtilities
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Montoya API Documentation:
// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html
// Montoya Extension Examples: https://github.com/PortSwigger/burp-extensions-montoya-api-examples

class ApplyAnomalyRank : BurpExtension, ContextMenuItemsProvider {
    private val requestResponses = mutableListOf<HttpRequestResponse>()
    private lateinit var api: MontoyaApi
    private val applyAnomalyRankMenuItem = JMenuItem("Apply Anomaly Rank")

    // private val projectSettings : MyProjectSettings by lazy { MyProjectSettings() }

    companion object {
        const val EXTENSION_NAME = "Apply Anomaly Rank"
    }

    override fun initialize(api: MontoyaApi?) {

        this.api = requireNotNull(api) { "api : MontoyaApi is not allowed to be null" }
        // This will print to Burp Suite's Extension output and can be used to debug whether the
        // extension loaded properly
        api.logging().logToOutput("Started loading the extension v0.1.8...")

        // Name our extension when it is displayed inside of Burp Suite
        api.extension().setName(EXTENSION_NAME)

        // Code for setting up your extension starts here...

        applyAnomalyRankMenuItem.addActionListener { e -> applyAnomalyRank() }

        api.userInterface().registerContextMenuItemsProvider(this)

        // Just a simple hello world to start with

        // Code for setting up your extension ends here
        // api.userInterface().registerSettingsPanel(projectSettings.settingsPanel)

        // See logging comment above
        api.logging().logToOutput("...Finished loading the extension")
    }

    @OptIn(ExperimentalTime::class)
    private fun applyAnomalyRank() {
        Thread.ofVirtual().start {
            val rankedRequests = api.utilities().rankingUtils().rank(requestResponses)
            val timestamp = Clock.System.now().epochSeconds
            val maxRank = rankedRequests.maxOf { it.rank() }
            val maxLength = maxRank.toString().length
            val resultsForUi = mutableListOf<AnomalyRankResultsTable.ResultEntry>()

            for (i in requestResponses.indices) {

                if (i < rankedRequests.size) {
                    val floatRank = rankedRequests[i].rank()

                    resultsForUi.add(
                            AnomalyRankResultsTable.ResultEntry(
                                    rank = floatRank,
                                    method = requestResponses[i].request().method(),
                                    url = requestResponses[i].request().url(),
                                    statusCode = requestResponses[i].response()?.statusCode() ?: 0,
                                    requestResponse = requestResponses[i]
                            )
                    )
                }
            }

            SwingUtilities.invokeLater {
                val resultsTable = AnomalyRankResultsTable(api, resultsForUi)
                resultsTable.isVisible = true
            }
        }
    }

    override fun provideMenuItems(event: ContextMenuEvent?): List<Component?> {
        event?.let {
            val selectedList = it.selectedRequestResponses()
            if (selectedList.isNotEmpty()) {
                requestResponses.clear()
                requestResponses.addAll(selectedList)
                return listOf(applyAnomalyRankMenuItem)
            }

            val editorRR = it.messageEditorRequestResponse()
            if (editorRR.isPresent) {
                requestResponses.clear()
                requestResponses.add(editorRR.get().requestResponse())
                return listOf(applyAnomalyRankMenuItem)
            }
        }
        return emptyList()
    }
}
