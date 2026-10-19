package in.sarkarialert.app;

/**
 * SiteGuard — keeps government pages reachable when their servers melt down
 * (exam results day) or block bots.
 *
 * Ladder: direct load -> readable mirror (r.jina.ai fetches fresh content
 * server-side) -> Wayback Machine snapshot -> themed error page.
 */
final class SiteGuard {

    private SiteGuard() { }

    /** fresh, readable text render of the page, fetched away from your phone */
    static String mirror(String url) {
        return "https://r.jina.ai/" + url;
    }

    /** last resort: the Internet Archive's nearest snapshot */
    static String archive(String url) {
        return "https://web.archive.org/web/" + url;
    }
}
