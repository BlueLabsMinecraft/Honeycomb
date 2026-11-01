package dev.bluelabs.honeycomb.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects configuration problems discovered while loading or reloading.
 *
 * <p>Issues never throw during a reload: an invalid setting is reported, a safe
 * default is used and the rest of the plugin keeps working.</p>
 */
public final class ValidationReport {

    /** How serious a configuration problem is. */
    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    /**
     * A single reported problem.
     *
     * @param severity how serious the problem is
     * @param source   file or system the problem came from
     * @param message  human readable description
     */
    public record Issue(Severity severity, String source, String message) {

        /**
         * @return the issue rendered for a console log line
         */
        public String format() {
            return "[" + source + "] " + message;
        }
    }

    private final List<Issue> issues = new ArrayList<>();

    /**
     * Records an informational note.
     *
     * @param source  file or system name
     * @param message description
     */
    public void info(String source, String message) {
        issues.add(new Issue(Severity.INFO, source, message));
    }

    /**
     * Records a recoverable problem.
     *
     * @param source  file or system name
     * @param message description
     */
    public void warning(String source, String message) {
        issues.add(new Issue(Severity.WARNING, source, message));
    }

    /**
     * Records a problem that prevented a feature from loading.
     *
     * @param source  file or system name
     * @param message description
     */
    public void error(String source, String message) {
        issues.add(new Issue(Severity.ERROR, source, message));
    }

    /**
     * @return an unmodifiable view of every issue
     */
    public List<Issue> issues() {
        return Collections.unmodifiableList(issues);
    }

    /**
     * @param severity severity to filter by
     * @return issues of that severity
     */
    public List<Issue> issues(Severity severity) {
        List<Issue> filtered = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue.severity() == severity) {
                filtered.add(issue);
            }
        }
        return filtered;
    }

    /**
     * @return true when at least one error was recorded
     */
    public boolean hasErrors() {
        return !issues(Severity.ERROR).isEmpty();
    }

    /**
     * @return true when at least one warning was recorded
     */
    public boolean hasWarnings() {
        return !issues(Severity.WARNING).isEmpty();
    }

    /**
     * @return a one line summary such as {@code 2 warnings, 0 errors}
     */
    public String summary() {
        return issues(Severity.WARNING).size() + " warning(s), "
                + issues(Severity.ERROR).size() + " error(s)";
    }

    /**
     * Removes every recorded issue.
     */
    public void clear() {
        issues.clear();
    }
}
