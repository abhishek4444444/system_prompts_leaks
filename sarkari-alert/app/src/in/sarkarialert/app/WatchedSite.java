package in.sarkarialert.app;

public class WatchedSite {
    public long id;
    public String url = "";
    public String label = "";
    public String mode = "page"; // "feed" or "page"
    public boolean enabled = true;
    public String lastHash = "";
    public String snapshot = ""; // JSON array of known link URLs (page mode)
    public long lastChecked;
    public String lastError;
    public int lastNew;
}
