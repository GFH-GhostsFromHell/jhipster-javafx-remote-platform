package com.gfh.electricalworkbench

import android.webkit.WebView
import org.json.JSONObject

/**
 * Shared responsive safety net for all embedded portal screens.
 * Preserves each web tool's own colours and calculations while ensuring
 * field controls remain readable on narrow Android screens / larger font scales.
 */
internal fun applyMobileWebStyle(webView: WebView) {
    val css = """
        @media (max-width: 720px) {
            html, body { max-width: 100% !important; overflow-x: hidden !important; }
            main, section, article, .wrap, .shell, .main, .screen, .card, .field,
            .quick-card, .quick-summary, .mark, .cue, .viewer {
                max-width: 100% !important;
                min-width: 0 !important;
                overflow-wrap: anywhere !important;
            }
            input:not([type="checkbox"]):not([type="radio"]), select, textarea {
                max-width: 100% !important;
                min-width: 0 !important;
                font-size: 16px !important;
                min-height: 44px !important;
            }
            button, .btn, .bendbtn, [role="button"] {
                max-width: 100% !important;
                min-height: 44px !important;
                font-size: 15px !important;
                line-height: 1.35 !important;
                white-space: normal !important;
                overflow-wrap: anywhere !important;
            }
            .actions, .advanced-actions, .quick-actions, .toolbar, header nav {
                flex-wrap: wrap !important;
                gap: 8px !important;
            }
            header nav { display: flex !important; align-items: center !important; }
            .fields, .quick-fields, .inputs, .marklist, .cues, .ops,
            .workspace, .grid, .refgrid, .welder-row {
                grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
            }
            .field, .actions > *, .advanced-actions > *, .toolbar > * {
                min-width: 0 !important;
            }
            canvas, svg, img { max-width: 100% !important; }
            .stepnav { grid-template-columns: repeat(2, minmax(0, 1fr)) !important; }
            .hint { max-width: 80%; font-size: 12px !important; }
        }
        @media (min-width: 390px) and (max-width: 520px) {
            .actions > button, .advanced-actions > button, .quick-actions > button {
                flex: 1 1 155px !important;
                width: auto !important;
            }
        }
        @media (max-width: 520px) {
            .fields, .quick-fields, .inputs, .marklist, .cues, .ops,
            .workspace, .grid, .refgrid, .welder-row {
                grid-template-columns: minmax(0, 1fr) !important;
            }
            .ops button, .quick-fields .field:first-child { grid-column: auto !important; }
            .actions > button, .advanced-actions > button, .quick-actions > button {
                flex: 1 1 155px !important; width: auto !important;
            }
            .top, .backrow { flex-wrap: wrap !important; }
            .card { padding: 12px !important; }
            .mark strong, .cue strong, .quick-mark { overflow-wrap: anywhere !important; }
            .steptext { min-height: 0 !important; }
        }
    """.trimIndent()
    val script = "(function(){var id='ew-android-readable-layout';" +
        "var style=document.getElementById(id);" +
        "if(!style){style=document.createElement('style');style.id=id;document.head.appendChild(style);}" +
        "style.textContent=" + JSONObject.quote(css) + ";})();"
    webView.evaluateJavascript(script, null)
}
