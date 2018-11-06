package picocli.model;

public class NoVersionProvider implements IVersionProvider {
    public String[] getVersion() throws Exception {
        throw new UnsupportedOperationException();
    }
}