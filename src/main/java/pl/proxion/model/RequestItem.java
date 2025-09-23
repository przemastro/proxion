package pl.proxion.model;

public abstract class RequestItem {
    protected String name;

    public RequestItem(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}