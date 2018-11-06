package picocli.model;

import picocli.CommandLine;

public class NoDefaultProvider implements IDefaultValueProvider {
    public String defaultValue(ArgSpec argSpec) {
        throw new UnsupportedOperationException();
    }
}