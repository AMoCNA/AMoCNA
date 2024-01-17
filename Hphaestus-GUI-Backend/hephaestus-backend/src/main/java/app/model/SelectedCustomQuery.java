package app.model;

public class SelectedCustomQuery implements SelectedQuery {

    private final String tag;
    private final String queryString;

    public SelectedCustomQuery(String tag, String queryString) {
        this.tag = tag;
        this.queryString = queryString;
    }

    @Override
    public String getTag() {
        return tag;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

}
