package ai.agentrunr.tools.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Browser automation tool powered by Playwright for Java.
 * Provides the AI agent with the ability to navigate web pages,
 * interact with elements, take screenshots, and extract content.
 */
public class PlaywrightBrowserTool implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(PlaywrightBrowserTool.class);

    private static final int MAX_TEXT_LENGTH = 10_000;

    private final boolean headless;

    private Playwright playwright;
    private Browser browser;
    private Page page;

    protected PlaywrightBrowserTool(boolean headless) {
        this.headless = headless;
    }

    private synchronized Page getOrCreatePage() {
        if (playwright == null) {
            logger.info("Initializing Playwright...");
            playwright = Playwright.create();
            browser = launchBrowser();
        }
        if (page == null || page.isClosed()) {
            page = browser.newPage();
        }
        return page;
    }

    private Browser launchBrowser() {
        try {
            return playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
        }
        catch (PlaywrightException e) {
            if (e.getMessage() != null && e.getMessage().contains("Executable doesn't exist")) {
                logger.info("Chromium not found — installing. This may take a moment...");
                try {
                    com.microsoft.playwright.CLI.main(new String[]{"install", "chromium"});
                }
                catch (Exception installEx) {
                    throw new RuntimeException("Failed to install Chromium: " + installEx.getMessage(), installEx);
                }
                return playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
            }
            throw e;
        }
    }

    @Tool(description = "Navigate to a URL in the browser. Returns the page title and a text summary of the page content.")
    public synchronized String navigateTo(
            @ToolParam(description = "The URL to navigate to") String url) {
        try {
            Page p = getOrCreatePage();
            p.navigate(url);
            p.waitForLoadState();
            String title = p.title();
            String text = truncate(p.innerText("body"));
            return "Title: " + title + "\n\nContent:\n" + text;
        }
        catch (Exception e) {
            return "Error navigating to " + url + ": " + e.getMessage();
        }
    }

    @Tool(description = "Click an element on the current page matching the given CSS selector.")
    public synchronized String clickElement(
            @ToolParam(description = "CSS selector for the element to click") String selector) {
        try {
            Page p = getOrCreatePage();
            p.click(selector);
            p.waitForLoadState();
            return "Clicked element: " + selector + "\nCurrent URL: " + p.url() + "\nTitle: " + p.title();
        }
        catch (Exception e) {
            return "Error clicking " + selector + ": " + e.getMessage();
        }
    }

    @Tool(description = "Fill an input field on the current page with the given value.")
    public synchronized String fillInput(
            @ToolParam(description = "CSS selector for the input field") String selector,
            @ToolParam(description = "The value to fill into the input") String value) {
        try {
            Page p = getOrCreatePage();
            p.fill(selector, value);
            return "Filled input " + selector + " with value.";
        }
        catch (Exception e) {
            return "Error filling " + selector + ": " + e.getMessage();
        }
    }

    @Tool(description = "Extract text content from elements matching a CSS selector. Use 'body' for the full page text.")
    public synchronized String getText(
            @ToolParam(description = "CSS selector to extract text from (use 'body' for full page)") String selector) {
        try {
            Page p = getOrCreatePage();
            String text = p.innerText(selector);
            return truncate(text);
        }
        catch (Exception e) {
            return "Error getting text from " + selector + ": " + e.getMessage();
        }
    }

    @Tool(description = "Take a screenshot of the current page. Returns the file path to the saved PNG image.")
    public synchronized String takeScreenshot() {
        try {
            Page p = getOrCreatePage();
            Path tempFile = Files.createTempFile("playwright-screenshot-", ".png");
            p.screenshot(new Page.ScreenshotOptions().setPath(tempFile).setFullPage(true));
            return "Screenshot saved to: " + tempFile.toAbsolutePath();
        }
        catch (Exception e) {
            return "Error taking screenshot: " + e.getMessage();
        }
    }

    @Tool(description = "Execute JavaScript in the current page context and return the result as a string.")
    public synchronized String evaluateJavaScript(
            @ToolParam(description = "The JavaScript expression to evaluate") String expression) {
        try {
            Page p = getOrCreatePage();
            Object result = p.evaluate(expression);
            return result != null ? result.toString() : "null";
        }
        catch (Exception e) {
            return "Error evaluating JavaScript: " + e.getMessage();
        }
    }

    @Tool(description = "Wait for an element matching the CSS selector to appear on the page.")
    public synchronized String waitForSelector(
            @ToolParam(description = "CSS selector to wait for") String selector,
            @ToolParam(description = "Maximum time to wait in milliseconds (default 5000)") int timeoutMs) {
        try {
            Page p = getOrCreatePage();
            int timeout = timeoutMs > 0 ? timeoutMs : 5000;
            p.waitForSelector(selector, new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(timeout));
            return "Element " + selector + " is now visible.";
        }
        catch (Exception e) {
            return "Timeout waiting for " + selector + ": " + e.getMessage();
        }
    }

    @Tool(description = "Close the browser session and release all resources.")
    public synchronized String closeBrowser() {
        try {
            closeResources();
            return "Browser session closed.";
        }
        catch (Exception e) {
            return "Error closing browser: " + e.getMessage();
        }
    }

    @Override
    public synchronized void close() {
        closeResources();
    }

    private void closeResources() {
        if (page != null && !page.isClosed()) {
            page.close();
            page = null;
        }
        if (browser != null && browser.isConnected()) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    private static String truncate(String text) {
        if (text == null) return "";
        if (text.length() <= MAX_TEXT_LENGTH) return text;
        return text.substring(0, MAX_TEXT_LENGTH) + "\n\n[Content truncated — " + text.length() + " total characters]";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean headless = true;

        public Builder headless(boolean headless) {
            this.headless = headless;
            return this;
        }

        public PlaywrightBrowserTool build() {
            return new PlaywrightBrowserTool(headless);
        }
    }
}
